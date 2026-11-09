package com.qonics.somanet;

import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Created by Allyblaise on 1/12/2018.
 */

public class Menu_row {
    LinearLayout lnlmain;
    TextView txttitle,txtdesc;

    public Menu_row(LinearLayout lnlmain,TextView txttitle, TextView txtdesc) {
        this.txttitle = txttitle;
        this.txtdesc = txtdesc;
        this.lnlmain = lnlmain;
    }

    public TextView getTxttitle() {
        return txttitle;
    }

    public TextView getTxtdesc() {
        return txtdesc;
    }

    public LinearLayout getLnlmain() {
        return lnlmain;
    }
}
