package org.jetbrains.jet.lang.cfg.pseudocode;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.cfg.Label;

import java.io.PrintStream;
import java.util.*;

/**
* @author abreslav
*/
public class Pseudocode {

    public class PseudocodeLabel implements Label {
        private final String name;
        private Integer targetInstructionIndex;


        private PseudocodeLabel(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }

        public int getTargetInstructionIndex() {
            return targetInstructionIndex;
        }

        public void setTargetInstructionIndex(int targetInstructionIndex) {
            this.targetInstructionIndex = targetInstructionIndex;
        }

        @Nullable
        private List<Instruction> resolve() {
            assert targetInstructionIndex != null;
            return instructions.subList(getTargetInstructionIndex(), instructions.size());
        }

    }

    private final List<Instruction> instructions = new ArrayList<Instruction>();
    private final List<PseudocodeLabel> labels = new ArrayList<PseudocodeLabel>();

    public PseudocodeLabel createLabel(String name) {
        PseudocodeLabel label = new PseudocodeLabel(name);
        labels.add(label);
        return label;
    }

    public void addInstruction(Instruction instruction) {
        instructions.add(instruction);
    }

    public void bindLabel(Label label) {
        ((PseudocodeLabel) label).setTargetInstructionIndex(instructions.size());
    }

    public void postProcess() {
        for (int i = 0, instructionsSize = instructions.size(); i < instructionsSize; i++) {
            Instruction instruction = instructions.get(i);
            final int currentPosition = i;
            instruction.accept(new InstructionVisitor() {
                @Override
                public void visitInstructionWithNext(InstructionWithNext instruction) {
                    instruction.setNext(getNextPosition(currentPosition));
                }

                @Override
                public void visitJump(AbstractJumpInstruction instruction) {
                    instruction.setResolvedTarget(getJumpTarget(instruction.getTargetLabel()));
                }

                @Override
                public void visitNondeterministicJump(NondeterministicJumpInstruction instruction) {
                    instruction.setNext(getNextPosition(currentPosition));
                    visitJump(instruction);
                }

                @Override
                public void visitConditionalJump(ConditionalJumpInstruction instruction) {
                    Instruction nextInstruction = getNextPosition(currentPosition);
                    Instruction jumpTarget = getJumpTarget(instruction.getTargetLabel());
                    if (instruction.onTrue()) {
                        instruction.setNextOnFalse(nextInstruction);
                        instruction.setNextOnTrue(jumpTarget);
                    }
                    else {
                        instruction.setNextOnFalse(jumpTarget);
                        instruction.setNextOnTrue(nextInstruction);
                    }
                    visitJump(instruction);
                }

                @Override
                public void visitFunctionLiteralValue(FunctionLiteralValueInstruction instruction) {
                    instruction.getBody().postProcess();
                    super.visitFunctionLiteralValue(instruction);
                }

                @Override
                public void visitSubroutineExit(SubroutineExitInstruction instruction) {
                    // Nothing
                }

                @Override
                public void visitInstruction(Instruction instruction) {
                    throw new UnsupportedOperationException(instruction.toString());
                }
            });
        }
    }

    @NotNull
    private Instruction getJumpTarget(@NotNull Label targetLabel) {
        return getTargetInstruction(((PseudocodeLabel) targetLabel).resolve());
    }

    @NotNull
    private Instruction getTargetInstruction(@NotNull List<Instruction> instructions) {
        while (true) {
            assert instructions != null;
            Instruction targetInstruction = instructions.get(0);

            if (false == targetInstruction instanceof UnconditionalJumpInstruction) {
                return targetInstruction;
            }

            Label label = ((UnconditionalJumpInstruction) targetInstruction).getTargetLabel();
            instructions = ((PseudocodeLabel)label).resolve();
        }
    }

    @NotNull
    private Instruction getNextPosition(int currentPosition) {
        int targetPosition = currentPosition + 1;
        assert targetPosition < instructions.size() : currentPosition;
        return getTargetInstruction(instructions.subList(targetPosition, instructions.size()));
    }

    public void dumpInstructions(@NotNull PrintStream out) {
        List<Pseudocode> locals = new ArrayList<Pseudocode>();
        for (int i = 0, instructionsSize = instructions.size(); i < instructionsSize; i++) {
            Instruction instruction = instructions.get(i);
            if (instruction instanceof FunctionLiteralValueInstruction) {
                FunctionLiteralValueInstruction functionLiteralValueInstruction = (FunctionLiteralValueInstruction) instruction;
                locals.add(functionLiteralValueInstruction.getBody());
            }
            for (PseudocodeLabel label: labels) {
                if (label.getTargetInstructionIndex() == i) {
                    out.println(label.getName() + ":");
                }
            }
            out.println("    " + instruction);
        }
        for (Pseudocode local : locals) {
            local.dumpInstructions(out);
        }
    }

    public void dumpGraph(@NotNull final PrintStream out) {
        String graphHeader = "digraph g";
        dumpSubgraph(out, graphHeader, new int[1], "", new HashMap<Instruction, String>());
    }

    private void dumpSubgraph(final PrintStream out, String graphHeader, final int[] count, String style, final Map<Instruction, String> nodeToName) {
        out.println(graphHeader + " {");
        out.println(style);

        dumpNodes(out, count, nodeToName);
        dumpEdges(out, count, nodeToName);

        out.println("}");
    }

    public void dumpEdges(final PrintStream out, final int[] count, final Map<Instruction, String> nodeToName) {
        for (final Instruction fromInst : instructions) {
            fromInst.accept(new InstructionVisitor() {
                @Override
                public void visitFunctionLiteralValue(FunctionLiteralValueInstruction instruction) {
                    int index = count[0];
//                    instruction.getBody().dumpSubgraph(out, "subgraph cluster_" + index, count, "color=blue;\nlabel = \"f" + index + "\";", nodeToName);
                    printEdge(out, nodeToName.get(instruction), nodeToName.get(instruction.getBody().instructions.get(0)), null);
                    visitInstructionWithNext(instruction);
                }

                @Override
                public void visitUnconditionalJump(UnconditionalJumpInstruction instruction) {
                    // Nothing
                }

                @Override
                public void visitJump(AbstractJumpInstruction instruction) {
                    printEdge(out, nodeToName.get(instruction), nodeToName.get(instruction.getResolvedTarget()), null);
                }

                @Override
                public void visitNondeterministicJump(NondeterministicJumpInstruction instruction) {
                    visitJump(instruction);
                    printEdge(out, nodeToName.get(instruction), nodeToName.get(instruction.getNext()), null);
                }

                @Override
                public void visitReturnValue(ReturnValueInstruction instruction) {
                    super.visitReturnValue(instruction);
                }

                @Override
                public void visitReturnNoValue(ReturnNoValueInstruction instruction) {
                    super.visitReturnNoValue(instruction);
                }

                @Override
                public void visitConditionalJump(ConditionalJumpInstruction instruction) {
                    String from = nodeToName.get(instruction);
                    printEdge(out, from, nodeToName.get(instruction.getNextOnFalse()), "no");
                    printEdge(out, from, nodeToName.get(instruction.getNextOnTrue()), "yes");
                }

                @Override
                public void visitInstructionWithNext(InstructionWithNext instruction) {
                    printEdge(out, nodeToName.get(instruction), nodeToName.get(instruction.getNext()), null);
                }

                @Override
                public void visitSubroutineExit(SubroutineExitInstruction instruction) {
                    // Nothing
                }

                @Override
                public void visitInstruction(Instruction instruction) {
                    throw new UnsupportedOperationException(instruction.toString());
                }
            });
        }
    }

    public void dumpNodes(PrintStream out, int[] count, Map<Instruction, String> nodeToName) {
        for (Instruction node : instructions) {
            if (node instanceof UnconditionalJumpInstruction) {
                continue;
            }
            String name = "n" + count[0]++;
            nodeToName.put(node, name);
            String text = node.toString();
            int newline = text.indexOf("\n");
            if (newline >= 0) {
                text = text.substring(0, newline);
            }
            String shape = "box";
            if (node instanceof ConditionalJumpInstruction) {
                shape = "diamond";
            }
            else if (node instanceof NondeterministicJumpInstruction) {
                shape = "Mdiamond";
            }
            else if (node instanceof UnsupportedElementInstruction) {
                shape = "box, fillcolor=red, style=filled";
            }
            else if (node instanceof FunctionLiteralValueInstruction) {
                shape = "Mcircle";
            }
            else if (node instanceof SubroutineEnterInstruction || node instanceof SubroutineExitInstruction) {
                shape = "roundrect, style=rounded";
            }
            out.println(name + "[label=\"" + text + "\", shape=" + shape + "];");
        }
    }

    private void printEdge(PrintStream out, String from, String to, String label) {
        if (label != null) {
            label = "[label=\"" + label + "\"]";
        }
        else {
            label = "";
        }
        out.println(from + " -> " + to + label + ";");
    }


}