package org.aerogear.mobile.security.checks;


import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.support.annotation.NonNull;

/**
 * Detects whether a devices filesystem is encrypted
 */
public class EncryptionEnabledCheck extends AbstractDeviceCheck {
    /**
     * Check if a devices filesystem is encrypted. Filesystem-level encryption makes harder for 3rd
     * parties to access users' data.
     *
     * @param context Context to be used by the check.
     * @return <code>true</code> if the encryption have been enabled on the device.
     */
    @Override
    protected boolean execute(@NonNull final Context context) {
        final DevicePolicyManager policyManager = (DevicePolicyManager) context
                        .getSystemService(Context.DEVICE_POLICY_SERVICE);
        return policyManager != null && policyManager
                        .getStorageEncryptionStatus() == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE;
    }

    @Override
    public String getName() {
        return "Encryption Check";
    }
}
