package org.jenkinsci.plugins.IBM_zOS_Connector;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

class PermissionsChecker {
    static FormValidation CheckCredentialsID(
            @AncestorInPath Item item,
            @QueryParameter String value) {
        if (item == null) {
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                return FormValidation.ok();
            }
        } else {
            if (!item.hasPermission(Item.EXTENDED_READ)
                    && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                return FormValidation.ok();
            }
        }
        if (StringUtils.isBlank(value)) {
            return FormValidation.ok();
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            return FormValidation.warning("Cannot validate expression based credentials");
        }
        return null;
    }

    static ListBoxModel FillBasicCredentials(
            @AncestorInPath Item item,
            @QueryParameter String credentialsId) {
        if (item == null) {
            try {
                boolean admin = Jenkins.get().hasPermission(Jenkins.ADMINISTER);
                if (!admin) return new StandardListBoxModel().includeCurrentValue(credentialsId);
            } catch (IllegalStateException ignored) {
            }
        } else {
            if (!item.hasPermission(Item.EXTENDED_READ)
                    && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }
        }
        return null;
    }
}
