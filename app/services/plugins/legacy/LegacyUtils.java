package services.plugins.legacy;

import play.mvc.Call;

/**
 * Utilities for the "legacy" plugins = not using the new extension framework
 * @author Pierre-Yves Cloux
 */
public class LegacyUtils {

    public LegacyUtils() {
    }

    public static Call callFromString(String urlAsString){
        return new Call(){
            @Override
            public String fragment() {
                return "";
            }

            @Override
            public String method() {
                return "";
            }

            @Override
            public String url() {
                return urlAsString;
            }};
    }
}
