/*
 * @author alex.tkachman
 */
package jet;

import jet.typeinfo.TypeInfo;
public abstract class ExtensionFunction14<E, D1, D2, D3, D4, D5, D6, D7, D8, D9, D10, D11, D12, D13, D14, R> extends DefaultJetObject {
    protected ExtensionFunction14(TypeInfo<?> typeInfo) {
        super(typeInfo);
    }

    public abstract R invoke(E receiver, D1 d1, D2 d2, D3 d3, D4 d4, D5 d5, D6 d6, D7 d7, D8 d8, D9 d9, D10 d10, D11 d11, D12 d12, D13 d13, D14 d14);

    @Override
    public String toString() {
      return "{E.(d1: D1, d2: D2, d3: D3, d4: D4, d5: D5, d6: D6, d7: D7, d8: D8, d9: D9, d10: D10, d11: D11, d12: D12, d13: D13, d14: D14) : R)}";
    }
}
