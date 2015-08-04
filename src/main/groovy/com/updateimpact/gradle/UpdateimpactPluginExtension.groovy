package com.updateimpact.gradle

class UpdateimpactPluginExtension {

    /**
     * API key from http://updateimpact.com
     */
    String apiKey

    /**
     * If set to true, a browser will be automatically opened after plugin run is complete.
     */
    boolean openBrowser = true

    /**
     * Updateimpact server URL
     */
    String url = "https://app.updateimpact.com"
}
