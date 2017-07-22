package com.mysafeatl.atlcomp.mysafeatl.api;

import java.util.List;
import com.mysafeatl.atlcomp.mysafeatl.models.Route;

public interface DirectionsAPIListener {

    void onDirectionStart();
    void onDirectionSuccess(List<Route> route);

}
