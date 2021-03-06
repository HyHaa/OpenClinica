package org.akaza.openclinica.service.crfdata.xform;

import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.akaza.openclinica.bean.core.Role;
import org.akaza.openclinica.bean.core.SubjectEventStatus;
import org.akaza.openclinica.dao.core.CoreResources;
import org.akaza.openclinica.domain.Status;
import org.akaza.openclinica.domain.datamap.FormLayout;
import org.akaza.openclinica.domain.datamap.FormLayoutMedia;
import org.akaza.openclinica.domain.datamap.Study;
import org.akaza.openclinica.domain.datamap.StudyEvent;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.encoding.ShaPasswordEncoder;
import org.springframework.web.client.RestTemplate;

public class EnketoAPI {

    private String enketoURL = null;
    private String token = null;
    private String ocURL = null;
    protected final Logger logger = LoggerFactory.getLogger(getClass().getName());
    public static final String QUERY_FLAVOR = "-query";
    public static final String SINGLE_ITEM_FLAVOR = "-single_item";
    public static final String VIEW_MODE = "view";
    public static final String EDIT_MODE = "edit";

    /*
     * SURVEY : Initial Data Entry
     * INSTANCE : Edit mode (passing in instance)
     * 100% READONLY: applies for both field and query and dn close button should not appear
     */

    public static final String SURVEY_PREVIEW_MODE = "/api/v2/survey/preview";
    public static final String SURVEY_OFFLINE_MODE = "/api/v2/survey/offline";

    public static final String SURVEY_100_PERCENT_READONLY = "/api/v2/survey/view/iframe";

    public static final String SURVEY_WRITABLE_DN = "/api/v2/survey/single/fieldsubmission/iframe";
    public static final String SURVEY_WRITABLE_DN_CLOSE_BUTTON = "/api/v2/survey/single/fieldsubmission/c/iframe";

    public static final String INSTANCE_100_PERCENT_READONLY = "/api/v2/instance/view/iframe";

    public static final String INSTANCE_READONLY_DN = "/api/v2/instance/fieldsubmission/note/iframe";
    public static final String INSTANCE_READONLY_DN_CLOSE_BUTTON = "/api/v2/instance/fieldsubmission/note/c/iframe";

    public static final String INSTANCE_WRITABLE_DN = "/api/v2/instance/fieldsubmission/iframe";
    public static final String INSTANCE_WRITABLE_DN_CLOSE_BUTTON = "/api/v2/instance/fieldsubmission/c/iframe";

    public EnketoAPI(EnketoCredentials credentials) {
        this.enketoURL = credentials.getServerUrl();
        this.token = credentials.getApiKey();
        this.ocURL = credentials.getOcInstanceUrl();
    }

    public String getOfflineFormURL(String crfOID) throws Exception {
        if (enketoURL == null)
            return "";
        URL eURL = new URL(enketoURL + SURVEY_OFFLINE_MODE);
        EnketoURLResponse response = getURL(eURL, crfOID);
        if (response != null) {
            String myUrl = response.getOffline_url();
            if (enketoURL.toLowerCase().startsWith("https") && !myUrl.toLowerCase().startsWith("https")) {
                myUrl = myUrl.replaceFirst("http", "https");
            }
            return myUrl;
        } else
            return "";
    }

    public String getFormURL(String crfOID, String studyOid, Role role, Study parentStudy, StudyEvent studyEvent, String mode) throws Exception {
        if (enketoURL == null)
            return "";

        URL eURL = null;

        // https://jira.openclinica.com/browse/OC-8269 Open Form when study is locked
        // https://jira.openclinica.com/browse/OC-8270 Open Form when event is locked
        // https://jira.openclinica.com/browse/OC-8276 Open Form when study is frozen
        // https://jira.openclinica.com/browse/OC-8275 Data Specialist views XForms.
        // https://jira.openclinica.com/browse/OC-8274 Data Entry Person views XForms.
        // https://jira.openclinica.com/browse/OC-8272 Investigator views XForms.
        // https://jira.openclinica.com/browse/OC-8273 CRC views XForms.
        // https://jira.openclinica.com/browse/OC-7573 Data Manager views XForms.
        // https://jira.openclinica.com/browse/OC-7574 Study Director views XForms.
        // https://jira.openclinica.com/browse/OC-7575 Monitor views XForms.
        if (parentStudy.getStatus().equals(Status.LOCKED)
                || (studyEvent != null && studyEvent.getSubjectEventStatusId().equals(SubjectEventStatus.LOCKED.getId()))
                || parentStudy.getStatus().equals(Status.FROZEN) || mode.equals(VIEW_MODE)) {
            eURL = new URL(enketoURL + SURVEY_100_PERCENT_READONLY);

            // https://jira.openclinica.com/browse/OC-8267 Data Specialist edits XForms.
            // https://jira.openclinica.com/browse/OC-8266 Data Entry Person edits XForms.
            // https://jira.openclinica.com/browse/OC-7572 Investigator edits XForms.
            // https://jira.openclinica.com/browse/OC-7571 CRC edits XForms.
        } else if (mode.equals(EDIT_MODE) && (role == Role.RESEARCHASSISTANT || role == Role.RESEARCHASSISTANT2 || role == Role.INVESTIGATOR)) {
            eURL = new URL(enketoURL + SURVEY_WRITABLE_DN);

            // https://jira.openclinica.com/browse/OC-8278 Data Manager edits XForms.
            // https://jira.openclinica.com/browse/OC-8279 Study Director edits XForms.
        } else if (mode.equals(EDIT_MODE) && (role == Role.STUDYDIRECTOR || role == Role.COORDINATOR)) {
            eURL = new URL(enketoURL + SURVEY_WRITABLE_DN_CLOSE_BUTTON);
        }
        String myUrl = null;
        EnketoURLResponse response = getURL(eURL, crfOID);
        if (response != null) {
            if (response.getSingle_fieldsubmission_iframe_url() != null) {
                myUrl = response.getSingle_fieldsubmission_iframe_url();
            } else if (response.getView_iframe_url() != null) {
                myUrl = response.getView_iframe_url();
            }

            if (enketoURL.toLowerCase().startsWith("https") && !myUrl.toLowerCase().startsWith("https")) {
                myUrl = myUrl.replaceFirst("http", "https");
            }
            return myUrl;
        } else
            return "";
    }

    public String getFormPreviewURL(String crfOID) throws Exception {
        if (enketoURL == null)
            return "";
        URL eURL = new URL(enketoURL + SURVEY_PREVIEW_MODE);
        EnketoURLResponse response = getURL(eURL, crfOID);
        if (response != null)
            return response.getPreview_url();
        else
            return "";
    }

    private EnketoURLResponse getURL(URL url, String crfOID) {
        try {
            String userPasswdCombo = new String(Base64.encodeBase64((token + ":").getBytes()));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Authorization", "Basic " + userPasswdCombo);
            headers.add("Accept-Charset", "UTF-8");
            EnketoURLRequest body = new EnketoURLRequest(ocURL, crfOID);
            HttpEntity<EnketoURLRequest> request = new HttpEntity<EnketoURLRequest>(body, headers);
            RestTemplate rest = new RestTemplate();
            ResponseEntity<EnketoURLResponse> response = rest.postForEntity(url.toString(), request, EnketoURLResponse.class);
            if (response != null)
                return response.getBody();
            else
                return null;

        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.error(ExceptionUtils.getStackTrace(e));
        }
        return null;
    }

    public EnketoURLResponse getEditURL(FormLayout formLayout, String crfFlavor, String instance, String ecid, String redirect, boolean markComplete,
            String studyOid, List<FormLayoutMedia> mediaList, String goTo, String flavor, Role role, Study parentStudy, StudyEvent studyEvent, String mode) {
        String crfOid = formLayout.getOcOid() + crfFlavor;
        if (enketoURL == null)
            return null;

        try {
            // Build instanceId to cache populated instance at Enketo with
            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            String hashString = ecid + "." + String.valueOf(cal.getTimeInMillis());
            ShaPasswordEncoder encoder = new ShaPasswordEncoder(256);
            String instanceId = encoder.encodePassword(hashString, null);
            URL eURL = null;

            // https://jira.openclinica.com/browse/OC-8270 Open Form when event is locked
            // https://jira.openclinica.com/browse/OC-8269 Open Form when study is locked
            if (parentStudy.getStatus().equals(Status.LOCKED) || studyEvent.getSubjectEventStatusId().equals(SubjectEventStatus.LOCKED.getId())) {
                eURL = new URL(enketoURL + INSTANCE_100_PERCENT_READONLY);
                markComplete = false;

                // https://jira.openclinica.com/browse/OC-8275 Data Specialist views XForms.
                // https://jira.openclinica.com/browse/OC-8274 Data Entry Person views XForms.
                // https://jira.openclinica.com/browse/OC-8272 Investigator views XForms.
                // https://jira.openclinica.com/browse/OC-8273 CRC views XForms.
            } else if (flavor.equals(QUERY_FLAVOR) && mode.equals(VIEW_MODE)
                    && (role == Role.RESEARCHASSISTANT || role == Role.RESEARCHASSISTANT2 || role == Role.INVESTIGATOR)) {
                eURL = new URL(enketoURL + INSTANCE_READONLY_DN);
                markComplete = false;

                // https://jira.openclinica.com/browse/OC-7575 Monitor views XForms.
                // https://jira.openclinica.com/browse/OC-7574 Study Director views XForms.
                // https://jira.openclinica.com/browse/OC-7573 Data Manager views XForms.
            } else if (flavor.equals(QUERY_FLAVOR) && mode.equals(VIEW_MODE)
                    && (role == Role.STUDYDIRECTOR || role == Role.COORDINATOR || role == Role.MONITOR)) {
                eURL = new URL(enketoURL + INSTANCE_READONLY_DN_CLOSE_BUTTON);
                markComplete = false;

                // https://jira.openclinica.com/browse/OC-8276 Open Form when study is frozen
                // https://jira.openclinica.com/browse/OC-8267 Data Specialist edits XForms.
                // https://jira.openclinica.com/browse/OC-8266 Data Entry Person edits XForms.
                // https://jira.openclinica.com/browse/OC-7572 Investigator edits XForms.
                // https://jira.openclinica.com/browse/OC-7571 CRC edits XForms.
            } else if (flavor.equals(QUERY_FLAVOR) && !parentStudy.getStatus().equals(Status.FROZEN) && mode.equals(EDIT_MODE)
                    && (role == Role.RESEARCHASSISTANT || role == Role.RESEARCHASSISTANT2 || role == Role.INVESTIGATOR)) {
                eURL = new URL(enketoURL + INSTANCE_WRITABLE_DN);

                // https://jira.openclinica.com/browse/OC-8276 Open Form when study is frozen
                // https://jira.openclinica.com/browse/OC-8279 Study Director edits XForms.
                // https://jira.openclinica.com/browse/OC-8278 Data Manager edits XForms.
            } else if (flavor.equals(QUERY_FLAVOR) && !parentStudy.getStatus().equals(Status.FROZEN) && mode.equals(EDIT_MODE)
                    && (role == Role.STUDYDIRECTOR || role == Role.COORDINATOR)) {
                eURL = new URL(enketoURL + INSTANCE_WRITABLE_DN_CLOSE_BUTTON);

                // https://jira.openclinica.com/browse/OC-8276 Open Form when study is frozen
            } else if (flavor.equals(QUERY_FLAVOR) && parentStudy.getStatus().equals(Status.FROZEN) && mode.equals(EDIT_MODE)
                    && (role == Role.RESEARCHASSISTANT || role == Role.RESEARCHASSISTANT2 || role == Role.INVESTIGATOR)) {
                eURL = new URL(enketoURL + INSTANCE_READONLY_DN);
                markComplete = false;

                // https://jira.openclinica.com/browse/OC-8276 Open Form when study is frozen
            } else if (flavor.equals(QUERY_FLAVOR) && parentStudy.getStatus().equals(Status.FROZEN) && mode.equals(EDIT_MODE)
                    && (role == Role.STUDYDIRECTOR || role == Role.COORDINATOR)) {
                eURL = new URL(enketoURL + INSTANCE_READONLY_DN_CLOSE_BUTTON);
                markComplete = false;

                // https://jira.openclinica.com/browse/OC-7575 Monitor views XForms.
            } else if (flavor.equals(QUERY_FLAVOR) && mode.equals(EDIT_MODE) && role == Role.MONITOR) {
                eURL = new URL(enketoURL + INSTANCE_READONLY_DN_CLOSE_BUTTON);
                markComplete = false;

                // View Queries for Individual fields
            } else if (flavor.equals(SINGLE_ITEM_FLAVOR) && (role == Role.RESEARCHASSISTANT || role == Role.RESEARCHASSISTANT2 || role == Role.INVESTIGATOR)) {
                eURL = new URL(enketoURL + INSTANCE_READONLY_DN);
                markComplete = false;
                // View Queries for Individual fields
            } else if (flavor.equals(SINGLE_ITEM_FLAVOR) && (role == Role.MONITOR || role == Role.STUDYDIRECTOR || role == Role.COORDINATOR)) {
                eURL = new URL(enketoURL + INSTANCE_READONLY_DN_CLOSE_BUTTON);
                markComplete = false;
            }

            String userPasswdCombo = new String(Base64.encodeBase64((token + ":").getBytes()));

            InstanceAttachment attachment = new InstanceAttachment();

            for (FormLayoutMedia media : mediaList) {
                String fileName = media.getName();
                String baseUrl = CoreResources.getField("sysURL.base") + "rest2/openrosa/" + studyOid;
                String downLoadUrl = baseUrl + "/downloadMedia?formLayoutMediaId=" + media.getFormLayoutMediaId();
                attachment.setAdditionalProperty(fileName, downLoadUrl);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Authorization", "Basic " + userPasswdCombo);
            headers.add("Accept-Charset", "UTF-8");
            EnketoEditURLRequest body = new EnketoEditURLRequest(ocURL, crfOid, instanceId, redirect, instance, String.valueOf(markComplete), attachment, goTo);
            HttpEntity<EnketoEditURLRequest> request = new HttpEntity<EnketoEditURLRequest>(body, headers);
            RestTemplate rest = new RestTemplate();
            ResponseEntity<EnketoURLResponse> response = rest.postForEntity(eURL.toString(), request, EnketoURLResponse.class);
            if (response != null)
                return response.getBody();
            else
                return null;

        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.error(ExceptionUtils.getStackTrace(e));
        }
        return null;
    }

}