package org.aerogear.android.ags.auth.credentials;

import org.json.JSONException;
import org.json.JSONObject;

public class IntegrityCheckParameters implements IIntegrityCheckParameters {

    private final String issuer;
    private final String audience;
    private final String publicKey;

    public IntegrityCheckParameters(final String audience, final String issuer, final String publicKey) {
        this.issuer = issuer;
        this.audience = audience;
        this.publicKey = publicKey;
    }

    public IntegrityCheckParameters() {
        this.issuer = null;
        this.audience = null;
        this.publicKey = null;
    }

    /**
     * Get the audience parameter.
     * @return audience parameter.
     */
    public String getIssuer() {
        return this.issuer;
    }

    /**
     * Get the issuer parameter.
     * @return issuer parameter.
     */
    public String getAudience() {
        return this.audience;
    }

    /**
     * Get the PEM encoded public key (RSA).
     * @return public key parameter.
     */
    public String getPublicKey() { return this.publicKey; }

    public String serialize() {
        try {
            return new JSONObject()
                .put("audience", this.audience)
                .put("issuer", this.issuer)
                .put("publicKey", this.publicKey)
                .toString();
        } catch(JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return json representation of the parameters
     * @return json string representation of parameters
     */
    public static IntegrityCheckParameters deserialize(final String serializedParams) {
        try {
            final JSONObject jsonParams = new JSONObject(serializedParams);
            final String audience = jsonParams.getString("audience");
            final String issuer = jsonParams.getString("issuer");
            final String publicKey = jsonParams.getString("publicKey");
            return new IntegrityCheckParameters(audience, issuer, publicKey);
        } catch(JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check whether the integrity check parameters are valid.
     * @return <code>true</code> if the parameters are valid.
     */
    public boolean isValid() {
        return issuer != null && audience != null && publicKey != null;
    }
}
