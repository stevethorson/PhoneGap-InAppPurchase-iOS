/**
 * In App Billing Plugin
 *
 * @author Guillaume Charhon - Smart Mobile Software
 * @modifications Brian Thurlow 10/16/13
 *
 */
package com.mohamnag.inappbilling;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.List;
import java.util.ArrayList;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import com.mohamnag.iab.IabHelper;
import com.mohamnag.iab.IabResult;
import com.mohamnag.util.Inventory;
import com.mohamnag.util.SkuDetails;

import android.content.Intent;
import android.util.Log;

public class InAppBillingPlugin extends CordovaPlugin {
    //TODO: transfer all the logs back to JS for a better visibility. ~> window.inappbilling.log()

    /*
     SIDE NOTE: plugins can initialize automatically using "initialize" method. they can even request on load
     init. may be considered too!

     http://docs.phonegap.com/en/3.4.0/guide_platforms_android_plugin.md.html#Android%20Plugins
     */
    /*
     Error codes.
     keep synchronized between: InAppPurchase.m, InAppBillingPlugin.java, android_iab.js and ios_iab.js

     Be carefull assiging new codes, these are meant to express the REASON of the error, not WHAT failed!
     */
    private static final int ERROR_CODES_BASE = 4983497;

    private static final int ERR_LOAD = ERROR_CODES_BASE + 2;
    private static final int ERR_PURCHASE = ERROR_CODES_BASE + 3;
    private static final int ERR_LOAD_RECEIPTS = ERROR_CODES_BASE + 4;
    private static final int ERR_CLIENT_INVALID = ERROR_CODES_BASE + 5;
    private static final int ERR_PAYMENT_CANCELLED = ERROR_CODES_BASE + 6;
    private static final int ERR_PAYMENT_INVALID = ERROR_CODES_BASE + 7;
    private static final int ERR_PAYMENT_NOT_ALLOWED = ERROR_CODES_BASE + 8;
    private static final int ERR_UNKNOWN = ERROR_CODES_BASE + 10;

    // used here:
    private static final int ERR_SETUP = ERROR_CODES_BASE + 1;
    private static final int ERR_LOAD_INVENTORY = ERROR_CODES_BASE + 11;
    private static final int ERR_HELPER_DISPOSED = ERROR_CODES_BASE + 12;
    private static final int ERR_NOT_INITIALIZED = ERROR_CODES_BASE + 13;
    private static final int ERR_INVENTORY_NOT_LOADED = ERROR_CODES_BASE + 14;
    private static final int ERR_PURCHASE_FAILED = ERROR_CODES_BASE + 15;
    private static final int ERR_JSON_CONVERSION_FAILED = ERROR_CODES_BASE + 16;
    private static final int ERR_INVALID_PURCHASE_PAYLOAD = ERROR_CODES_BASE + 16;

    private boolean initialized = false;

    //TODO: set this from JS, according to what is defined in options
    private final Boolean ENABLE_DEBUG_LOGGING = true;

    private final String TAG = "CORDOVA_BILLING";

    //TODO: move it to config file: https://github.com/poiuytrez/AndroidInAppBilling/pull/52/files
    /* base64EncodedPublicKey should be YOUR APPLICATION'S PUBLIC KEY
     * (that you got from the Google Play developer console). This is not your
     * developer public key, it's the *app-specific* public key.
     *
     * Instead of just storing the entire literal string here embedded in the
     * program,  construct the key at runtime from pieces or
     * use bit manipulation (for example, XOR with some other string) to hide
     * the actual key.  The key itself is not secret information, but we don't
     * want to make it easy for an attacker to replace the public key with one
     * of their own and then fake messages from the server.
     */
    private final String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAogC9VXkak0pUlNZLpT90jKyejwrsd6ASjL1wuIJgpk3TyoOEYR3aUdthTfVEnqsEdOWNb/uc0CsFfsnGIchiQmiL3oSM7WFpC4/zWVYl8M+oe3BWczEMKSC7XR/XXjsnK7dMWvPFkProF9+4yDCHy+zpPT0HKP0UZOp0GTNGjgKP2SIye0Whx985vo6edsrKeNe7aZZS63N8X6bRIMAHKgyO4vowZJn+QYGzHh9ZSknExfJFqBKhMr5ytI2shhzFMx0tQPd76SKjIRZ8e6iQAyJkMjLnCBbhfB4FoguSXijB4PCZxTJ0fmO6OGIhWf3hz/wLRapGlRXtEuV2HVTH5QIDAQAB";

    // (arbitrary) request code for the purchase flow
    static final int RC_REQUEST = 10001;

    // The helper object
    IabHelper mHelper;

    // A quite up to date inventory of available items and purchase items
    Inventory myInventory;

    //TODO: a global callbakcContext is wrong and may interfere in concurrent calls, remove this and pass it to each function!
    CallbackContext callbackContext;

    //TODO: replace all the Log.d() calls with this 
    private void jsLog(String msg) {
        //TODO: msg is prone to js injection! current workaround: turn off logs in production
        String js = String.format("window.inappbilling.log('%s');", "[android] " + msg);
        webView.sendJavascript(js);
    }

    @Override
    /**
     * Called by each javascript plugin function
     */
    public boolean execute(String action, JSONArray data, final CallbackContext callbackContext) {
        this.callbackContext = callbackContext;
        // Check if the action has a handler
        Boolean isValidAction = true;

        // Action selector
        try {
            switch (action) {
                // Initialize
                case "init": {
                    List<String> productIds = null;
                    if (data.length() > 0) {
                        productIds = jsonStringToList(data.getString(0));
                    }

                    init(productIds, callbackContext);
                    break;
                }
                // Get the list of purchases
                case "getPurchases": {
                    if (isReady(callbackContext)) {
                        getPurchases(callbackContext);
                    }
                    break;
                }
                // Buy an item
                case "buy": {
                    if (isReady(callbackContext)) {
                        buy(data.getString(0), data.getString(1), callbackContext);
                    }
                    break;
                }
                case "subscribe": {
                    // Subscribe to an item
                    // Get Product Id
                    final String sku = data.getString(0);
                    subscribe(sku);
                    break;
                }
                case "consumePurchase":
                    consumePurchase(data);
                    break;
                case "getAvailableProducts": {
                    // Get the list of purchases
                    JSONArray jsonSkuList = new JSONArray();
                    jsonSkuList = getAvailableProducts();
                    // Call the javascript back
                    callbackContext.success(jsonSkuList);
                    break;
                }
                case "getProductDetails": {
                    getProductDetails(jsonStringToList(data.getString(0)), callbackContext);
                    break;
                }
                default:
                    // No handler for the action
                    isValidAction = false;
                    break;
            }

        }
        catch (IllegalStateException e) {
            callbackContext.error(e.getMessage());
        }
        catch (JSONException e) {
            callbackContext.error(e.getMessage());
        }

        // Method not found
        return isValidAction;
    }

    /**
     * Helper to convert JSON string to a List<String>
     *
     * @param data
     * @return
     */
    private List<String> jsonStringToList(String data) {
        JSONArray jsonSkuList = new JSONArray(data);

        List<String> sku = new ArrayList<>();
        int len = jsonSkuList.length();

        jsLog("Num SKUs Found: " + len);

        for (int i = 0; i < len; i++) {
            sku.add(jsonSkuList.get(i).toString());
            jsLog("Product SKU Added: " + jsonSkuList.get(i).toString());
        }

        return sku;
    }

    /**
     * Initializes the plugin, will also optionally load products if some
     * product IDs are provided.
     *
     * @param productIds
     * @param callbackContext
     */
    private void init(final List<String> productIds, final CallbackContext callbackContext) {
        jsLog("Initialization started.");

        // Some sanity checks to see if the developer (that's you!) really followed the
        // instructions to run this plugin
        if (base64EncodedPublicKey.contains("CONSTRUCT_YOUR")) {
            throw new RuntimeException("Please put your app's public key in InAppBillingPlugin.java. See ReadMe.");
        }

        // Create the helper, passing it our context and the public key to verify signatures with
        jsLog("Creating IAB helper.");
        mHelper = new IabHelper(cordova.getActivity().getApplicationContext(), base64EncodedPublicKey);

        // enable debug logging (for a production application, you should set this to false).
        mHelper.enableDebugLogging(ENABLE_DEBUG_LOGGING);

        // Start setup. This is asynchronous and the specified listener
        // will be called once setup completes.
        jsLog("Starting IAB setup.");

        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            @Override
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    // Oh no, there was a problem.
                    jsLog("Setup finished unsuccessfully.");

                    callbackContext.error(ErrorEvent.buildJson(
                            ERR_SETUP,
                            "IAB setup was not successful",
                            result
                    ));

                }
                else {
                    // Hooray, IAB is fully set up.
                    jsLog("Setup finished successfully.");
                    initialized = true;

                    // Now, let's get an inventory of stuff we own.
                    getProductDetails(productIds, callbackContext);
                }
            }
        });
    }

    /**
     * Checks for correct initialization of plug-in and the case where helper is
     * disposed in mean time.
     *
     * @param result
     */
    private boolean isReady(CallbackContext callbackContext) {
        if (!initialized) {
            callbackContext.error(ErrorEvent.buildJson(
                    ERR_NOT_INITIALIZED,
                    "Plugin has not been initialized.",
                    null
            ));

            return false;
        }
        // Have we been disposed of in the meantime? If so, quit. (probably 
        // useless but lets keep it for now!) 
        else if (mHelper == null) {
            callbackContext.error(ErrorEvent.buildJson(
                    ERR_HELPER_DISPOSED,
                    "The billing helper has been disposed.",
                    null
            ));

            return false;
        }
        else {
            return true;
        }
    }

    /**
     * Buy an already loaded item.
     *
     * @param productId
     * @param payload
     * @param callbackContext
     */
    private void buy(final String productId, final String payload, final CallbackContext callbackContext) {
        this.cordova.setActivityResultCallback(this);

        // we create one listener for each purchase request, this guarnatiees 
        // the concistency of data when multiple requests is launched in parallel
        IabHelper.OnIabPurchaseFinishedListener prchListener = new IabHelper.OnIabPurchaseFinishedListener() {
            @Override
            public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
                jsLog("Purchase finished: " + result + ", purchase: " + purchase);

                if (isReady(callbackContext)) {
                    if (result.isFailure()) {
                        callbackContext.error(ErrorEvent.buildJson(
                                ERR_PURCHASE_FAILED,
                                "Purchase failed",
                                result
                        ));
                    }
                    else if (!payload.equals(purchase.getDeveloperPayload())) {
                        callbackContext.error(ErrorEvent.buildJson(
                                ERR_INVALID_PURCHASE_PAYLOAD,
                                "Developer payload verification failed.",
                                result
                        ));
                    }
                    else {
                        jsLog("Purchase successful.");

                        // add the purchase to the inventory
                        myInventory.addPurchase(purchase);

                        try {
                            callbackContext.success(new JSONObject(purchase.getOriginalJson()));
                        }
                        catch (JSONException e) {
                            callbackContext.error(ErrorEvent.buildJson(
                                    ERR_JSON_CONVERSION_FAILED,
                                    "Could not create JSON object from purchase object",
                                    result
                            ));
                        }
                    }
                }
            }
        };

        mHelper.launchPurchaseFlow(
                cordova.getActivity(),
                productId,
                RC_REQUEST,
                prchListener,
                payload
        );
    }

    // Buy an item
    private void subscribe(final String sku) {
        if (mHelper == null) {
            callbackContext.error("Billing plugin was not initialized");
            return;
        }
        if (!mHelper.subscriptionsSupported()) {
            callbackContext.error("Subscriptions not supported on your device yet. Sorry!");
            return;
        }

        /* TODO: for security, generate your payload here for verification. See the comments on 
         *        verifyDeveloperPayload() for more info. Since this is a sample, we just use 
         *        an empty string, but on a production app you should generate this. */
        final String payload = "";

        this.cordova.setActivityResultCallback(this);
        Log.d(TAG, "Launching purchase flow for subscription.");

        mHelper.launchPurchaseFlow(cordova.getActivity(), sku, IabHelper.ITEM_TYPE_SUBS, RC_REQUEST, mPurchaseFinishedListener, payload);
    }

    /**
     * Get list of loaded purchases
     *
     * @param callbackContext
     * @throws JSONException
     */
    private void getPurchases(CallbackContext callbackContext) throws JSONException {
        jsLog("getPurchases called.");
        if (myInventory == null) {
            callbackContext.error(ErrorEvent.buildJson(
                    ERR_INVENTORY_NOT_LOADED,
                    "Inventory is not loaded.",
                    null
            ));
        }
        else {
            List<Purchase> purchaseList = myInventory.getAllPurchases();

            // Convert the java list to JSON
            JSONArray jsonPurchaseList = new JSONArray();
            for (Purchase p : purchaseList) {
                jsonPurchaseList.put(new JSONObject(p.getOriginalJson()));
            }

            callbackContext.success(jsonPurchaseList);
        }
    }

    // Get the list of available products
    private JSONArray getAvailableProducts() {
        // Get the list of owned items
        if (myInventory == null) {
            callbackContext.error("Billing plugin was not initialized");
            return new JSONArray();
        }
        List<SkuDetails> skuList = myInventory.getAllProducts();

        // Convert the java list to buildJson
        JSONArray jsonSkuList = new JSONArray();
        try {
            for (SkuDetails sku : skuList) {
                Log.d(TAG, "SKUDetails: Title: " + sku.getTitle());
                jsonSkuList.put(sku.toJson());
            }
        }
        catch (JSONException e) {
            callbackContext.error(e.getMessage());
        }
        return jsonSkuList;
    }

    /**
     * Loads products with specific IDs and gets their details.
     *
     * @param productIds
     * @param callbackContext
     */
    private void getProductDetails(final List<String> productIds, final CallbackContext callbackContext) {
        if (isReady(callbackContext)) {
            IabHelper.QueryInventoryFinishedListener invListener = new IabHelper.QueryInventoryFinishedListener() {
                @Override
                public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
                    jsLog("Inventory listener called.");

                    if (result.isFailure()) {
                        callbackContext.error(ErrorEvent.buildJson(
                                ERR_LOAD_INVENTORY,
                                "Failed to query inventory.",
                                result
                        ));
                    }
                    else {
                        //I'm not really feeling good about just copying inventory OVER old data!
                        myInventory = inventory;

                        jsLog("Query inventory was successful.");
                        callbackContext.success();
                    }
                }
            };

            if (productIds == null) {
                jsLog("Querying inventory without product IDs.");
                mHelper.queryInventoryAsync(invListener);
            }
            else {
                jsLog("Querying inventory with specific product IDs.");
                mHelper.queryInventoryAsync(true, productIds, invListener);
            }
        }
    }

    // Consume a purchase
    private void consumePurchase(JSONArray data) throws JSONException {

        if (mHelper == null) {
            callbackContext.error("Did you forget to initialize the plugin?");
            return;
        }

        String sku = data.getString(0);

        // Get the purchase from the inventory
        Purchase purchase = myInventory.getPurchase(sku);
        if (purchase != null) // Consume it
        {
            mHelper.consumeAsync(purchase, mConsumeFinishedListener);
        }
        else {
            callbackContext.error(sku + " is not owned so it cannot be consumed");
        }
    }

    // Listener that's called when we finish querying the items and subscriptions we own
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.d(TAG, "Inside mGotInventoryListener");
            if (!hasErrorsAndUpdateInventory(result, inventory)) {

            }

            Log.d(TAG, "Query inventory was successful.");
            callbackContext.success();

        }
    };

    // Listener that's called when we finish querying the details
    IabHelper.QueryInventoryFinishedListener mGotDetailsListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.d(TAG, "Inside mGotDetailsListener");
            if (!hasErrorsAndUpdateInventory(result, inventory)) {

            }

            Log.d(TAG, "Query details was successful.");

            List<SkuDetails> skuList = inventory.getAllProducts();

            // Convert the java list to buildJson
            JSONArray jsonSkuList = new JSONArray();
            try {
                for (SkuDetails sku : skuList) {
                    Log.d(TAG, "SKUDetails: Title: " + sku.getTitle());
                    jsonSkuList.put(sku.toJson());
                }
            }
            catch (JSONException e) {
                callbackContext.error(e.getMessage());
            }
            callbackContext.success(jsonSkuList);
        }
    };

    // Check if there is any errors in the iabResult and update the inventory
    private Boolean hasErrorsAndUpdateInventory(IabResult result, Inventory inventory) {
        if (result.isFailure()) {
            callbackContext.error("Failed to query inventory: " + result);
            return true;
        }

        // Have we been disposed of in the meantime? If so, quit.
        if (mHelper == null) {
            callbackContext.error("The billing helper has been disposed");
            return true;
        }

        // Update the inventory
        myInventory = inventory;

        return false;
    }

    // Callback for when a purchase is finished
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);

            // Have we been disposed of in the meantime? If so, quit.
            if (mHelper == null) {
                callbackContext.error("The billing helper has been disposed");
            }

            if (result.isFailure()) {
                callbackContext.error("Error purchasing: " + result);
                return;
            }

            if (!verifyDeveloperPayload(purchase)) {
                callbackContext.error("Error purchasing. Authenticity verification failed.");
                return;
            }

            Log.d(TAG, "Purchase successful.");

            // add the purchase to the inventory
            myInventory.addPurchase(purchase);

            try {
                callbackContext.success(new JSONObject(purchase.getOriginalJson()));
            }
            catch (JSONException e) {
                callbackContext.error("Could not create JSON object from purchase object");
            }

        }
    };

    // Called when consumption is complete
    IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            Log.d(TAG, "Consumption finished. Purchase: " + purchase + ", result: " + result);

            // We know this is the "gas" sku because it's the only one we consume,
            // so we don't check which sku was consumed. If you have more than one
            // sku, you probably should check...
            if (result.isSuccess()) {
                // successfully consumed, so we apply the effects of the item in our
                // game world's logic

                // remove the item from the inventory
                myInventory.erasePurchase(purchase.getSku());
                Log.d(TAG, "Consumption successful. .");

                callbackContext.success(purchase.getOriginalJson());

            }
            else {
                callbackContext.error("Error while consuming: " + result);
            }

        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);

        // Pass on the activity result to the helper for handling
        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
        else {
            Log.d(TAG, "onActivityResult handled by IABUtil.");
        }
    }

    /**
     * Verifies the developer payload of a purchase.
     */
    boolean verifyDeveloperPayload(Purchase p) {
        @SuppressWarnings("unused")
        String payload = p.getDeveloperPayload();

        /*
         * TODO: verify that the developer payload of the purchase is correct. It will be
         * the same one that you sent when initiating the purchase.
         * 
         * WARNING: Locally generating a random string when starting a purchase and 
         * verifying it here might seem like a good approach, but this will fail in the 
         * case where the user purchases an item on one device and then uses your app on 
         * a different device, because on the other device you will not have access to the
         * random string you originally generated.
         *
         * So a good developer payload has these characteristics:
         * 
         * 1. If two different users purchase an item, the payload is different between them,
         *    so that one user's purchase can't be replayed to another user.
         * 
         * 2. The payload must be such that you can verify it even when the app wasn't the
         *    one who initiated the purchase flow (so that items purchased by the user on 
         *    one device work on other devices owned by the user).
         * 
         * Using your own server to store and verify developer payloads across app
         * installations is recommended.
         */
        return true;
    }

    // We're being destroyed. It's important to dispose of the helper here!
    @Override
    public void onDestroy() {
        super.onDestroy();

        // very important:
        Log.d(TAG, "Destroying helper.");
        if (mHelper != null) {
            mHelper.dispose();
            mHelper = null;
        }
    }

}
