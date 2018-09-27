//: "The contents of this file are subject to the Mozilla Public License
//: Version 1.1 (the "License"); you may not use this file except in
//: compliance with the License. You may obtain a copy of the License at
//: http://www.mozilla.org/MPL/
//:
//: Software distributed under the License is distributed on an "AS IS"
//: basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//: License for the specific language governing rights and limitations
//: under the License.
//:
//: The Original Code is Guanxi (http://www.guanxi.uhi.ac.uk).
//:
//: The Initial Developer of the Original Code is Alistair Young alistair@codebrane.com
//: All Rights Reserved.
//:

package org.guanxi.idp.service.saml2;

import org.apache.xmlbeans.XmlOptions;
import org.guanxi.common.GuanxiException;
import org.guanxi.common.definitions.SAML;
import org.guanxi.idp.service.GenericAuthHandler;
import org.guanxi.common.Utils;
import org.guanxi.common.metadata.SPMetadata;
import org.guanxi.common.definitions.Guanxi;
import org.guanxi.common.entity.EntityFarm;
import org.guanxi.common.entity.EntityManager;
import org.guanxi.common.trust.TrustUtils;
import org.guanxi.xal.saml_2_0.protocol.AuthnRequestDocument;
import org.guanxi.xal.saml_2_0.metadata.EntityDescriptorType;
import org.guanxi.xal.saml_2_0.metadata.IndexedEndpointType;
import org.apache.xmlbeans.XmlException;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.cert.X509Certificate;
import java.util.HashMap;

/**
 * Handles authenticating SAML2 Web Browser SSO connections
 * 
 * @author alistair
 */
public class WebBrowserSSOAuthHandler extends GenericAuthHandler {
  /** Our logger */
  private static final Logger logger = Logger.getLogger(WebBrowserSSOAuthHandler.class.getName());
  /** The default binding to use for the Response if none is specified or there's no endpoint match */
  private String defaultResponseBinding = null;

  /**
   * Takes care of authenticating the user and verifying the requesting entity.
   * As this handler can't display any errors it sets a request attribute:
   * wbsso-handler-error-message = error message text to display
   * if entity verification fails. The main handler can then display an message.
   * The handler can assume everything was ok if that attribute is not present.
   * 
   * @param request Servlet request
   * @param response Servlet response
   * @param object the object!
   * @return true to continue with the request otherwise false
   * @throws Exception if an error occurs
   */
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object object) throws Exception {
    String entityID = null;
    EntityManager manager = null;
    String requestBinding = null;
    AuthnRequestDocument requestDoc = null;

    // Sort out the incoming message encoding
    String encoding = null;
    // If requestBinding is there, we've already gone through the auth page via this handler
    if (request.getParameter("requestBinding") == null) {
      if (request.getMethod().equalsIgnoreCase("post")) {
        encoding = "post";
      }
      else {
        encoding = "get";
      }
    }
    else {
      if (request.getParameter("requestBinding").equals(SAML.SAML2_BINDING_HTTP_POST)) {
        encoding = "post";
      }
      else if (request.getParameter("requestBinding").equals(SAML.SAML2_BINDING_HTTP_REDIRECT)) {
        encoding = "get";
      }
    }

    try {
      if (encoding.equals("post")) {
        // HTTP-POST binding means a base64 encoded SAML Request
        requestDoc = AuthnRequestDocument.Factory.parse(new StringReader(Utils.decodeBase64(request.getParameter("SAMLRequest"))));
        requestBinding = SAML.SAML2_BINDING_HTTP_POST;
      }
      else {
        // HTTP-Redirect binding means a base64 encoded deflated SAML Request
        if (request.getParameter("SAMLRequest") != null) {
          byte[] decodedRequest = Utils.decodeBase64b(request.getParameter("SAMLRequest"));
          requestDoc = AuthnRequestDocument.Factory.parse(Utils.inflate(decodedRequest, Utils.RFC1951_NO_WRAP));
          requestBinding = SAML.SAML2_BINDING_HTTP_REDIRECT;
        }
        else {
          logger.error("got a get request with no SAMLRequest from " + request.getHeader("referer"));
          throw new GuanxiException("got a get request with no SAMLRequest from " + request.getHeader("referer"));
        }
      }

      entityID = requestDoc.getAuthnRequest().getIssuer().getStringValue();
      
      // Debug syphoning?
      if (idpConfig.getDebug() != null) {
        if (idpConfig.getDebug().getSypthonAttributeAssertions() != null) {
          HashMap<String, String> saml2Namespaces = new HashMap<String, String>();
          saml2Namespaces.put(SAML.NS_SAML_20_PROTOCOL, SAML.NS_PREFIX_SAML_20_PROTOCOL);
          saml2Namespaces.put(SAML.NS_SAML_20_ASSERTION, SAML.NS_PREFIX_SAML_20_ASSERTION);
          XmlOptions xmlOptions = new XmlOptions();
          xmlOptions.setSavePrettyPrint();
          xmlOptions.setSavePrettyPrintIndent(2);
          xmlOptions.setUseDefaultNamespace();
          xmlOptions.setSaveAggressiveNamespaces();
          xmlOptions.setSaveNamespacesFirst();

          if (idpConfig.getDebug().getSypthonAttributeAssertions().equals("yes")) {
            logger.info("=======================================================");
            logger.info("Request from " + entityID);
            logger.info("");
            StringWriter sw = new StringWriter();
            requestDoc.save(sw, xmlOptions);
            requestDoc.save(sw, xmlOptions);
            logger.info(sw.toString());
            logger.info("");
            logger.info("=======================================================");
          }
        }
      }

      // Pass the entityID to the service via the login page if required
      request.setAttribute("requestBinding", requestBinding);
      request.setAttribute("entityID", entityID);
      request.setAttribute("requestID", requestDoc.getAuthnRequest().getID());
      if (requestDoc.getAuthnRequest().getNameIDPolicy() != null) {
        request.setAttribute("NameIDFormat", requestDoc.getAuthnRequest().getNameIDPolicy().getFormat());
      }

      EntityFarm farm = (EntityFarm)servletContext.getAttribute(Guanxi.CONTEXT_ATTR_IDP_ENTITY_FARM);
      manager = farm.getEntityManagerForID(entityID);

      if (manager == null) {
        logger.error("no metadata for " + entityID);
        request.setAttribute("wbsso-handler-error-message",
                             messageSource.getMessage("sp.not.supported",
                                                      new Object[] {entityID},
                                                      request.getLocale()));
        return true;
      }

      // Verify the signature if there is one
      if (requestDoc.getAuthnRequest().getSignature() != null) {
        if (TrustUtils.verifySignature(requestDoc)) {
          X509Certificate[] x509FromSig = new X509Certificate[] {TrustUtils.getX509CertFromSignature(requestDoc.getAuthnRequest().getSignature().getKeyInfo())};
          if (!manager.getTrustEngine().trustEntity(manager.getMetadata(entityID), x509FromSig)) {
            logger.error("failed to trust " + entityID);
            request.setAttribute("wbsso-handler-error-message",
                                 messageSource.getMessage("sp.failed.verification",
                                                          new Object[] {entityID},
                                                          request.getLocale()));
          }
        }
        else {
          logger.error("failed to verify signature from " + entityID);
          request.setAttribute("wbsso-handler-error-message",
                               messageSource.getMessage("sp.signature.verification.failed",
                                                        null,
                                                        request.getLocale()));
        }
      }
    }
    catch(XmlException xe) {
      logger.error("Error verifying entity " + entityID, xe);
      request.setAttribute("wbsso-handler-error-message",
                           messageSource.getMessage("sp.failed.verification",
                                                    new Object[] {entityID},
                                                    request.getLocale()));
      return true;
    }

    // Entity verification was successful. Now get its attribute consumer URL
    // First, try to get the URL and binding from the SAML Request...
//    if ((requestDoc.getAuthnRequest().getAssertionConsumerServiceURL() != null) &&
//        (!requestDoc.getAuthnRequest().getAssertionConsumerServiceURL().equals("")) &&
//        (requestDoc.getAuthnRequest().getProtocolBinding() != null) &&
//        (!requestDoc.getAuthnRequest().getProtocolBinding().equals(""))) {
    if ((requestDoc.getAuthnRequest().getAssertionConsumerServiceURL() != null) &&
        (!requestDoc.getAuthnRequest().getAssertionConsumerServiceURL().equals(""))) {
      String acsURLFromRequest = requestDoc.getAuthnRequest().getAssertionConsumerServiceURL();

      String bindingFromRequest;
      if ((requestDoc.getAuthnRequest().getProtocolBinding() == null) ||
          (requestDoc.getAuthnRequest().getProtocolBinding().equals(""))) {
        bindingFromRequest = defaultResponseBinding;
      }
      else {
        bindingFromRequest = requestDoc.getAuthnRequest().getProtocolBinding();
      }

      if (validateACSAndBinding(manager, entityID, acsURLFromRequest, bindingFromRequest)) {
        request.setAttribute("acsURL", acsURLFromRequest);
        request.setAttribute("responseBinding", bindingFromRequest);
      }
      else {
        // The SP has requested we send the response to a particular ACS but we have no record
        // of it in the metadata, so refuse the request.
        request.setAttribute("wbsso-handler-error-message", "Invalid Attribute Consumer Service URL specified in request");
      }
    }

    // We don't support passive authentication
    if (requestDoc.getAuthnRequest().getIsPassive()) {
      request.setAttribute("wbsso-handler-error-message", SAML.SAML2_STATUS_NO_PASSIVE);
    }

    // Display the error without going through user authentication
    if (request.getAttribute("wbsso-handler-error-message") != null) {
      return true;
    }

    /* Redirects to the authentication page as required. This is to authenticate the user.
     * We'll end up back here after the user has logged in.
     */
    return auth(entityID, request, response);
  }

  private boolean validateACSAndBinding(EntityManager manager, String entityID, String acsURLFromRequest, String bindingFromRequest) {
    SPMetadata metadata = (SPMetadata)manager.getMetadata(entityID);
    EntityDescriptorType saml2Metadata = (EntityDescriptorType)metadata.getPrivateData();
    IndexedEndpointType[] acss = saml2Metadata.getSPSSODescriptorArray(0).getAssertionConsumerServiceArray();

    // Can be more than one valid ACS
    for (IndexedEndpointType acs : acss) {
      if ((acs.getBinding().equals(bindingFromRequest)) &&
          (acs.getLocation().equals(acsURLFromRequest))) {
        return true;
      }
    }

    return false;
  }

  /**
   * Retrieves the Attribute Consumer Service URL for a particular binding by looking
   * in the metadata for a Service Provider.
   *
   * @param manager EntityManager instance that can get at the metadata
   * @param entityID the identifier of the Service Provider
   * @param requestBinding either SAML.SAML2_BINDING_HTTP_POST or SAML.SAML2_BINDING_HTTP_REDIRECT
   * @param defaultResponseBinding whatever is set in web-browser-ss-auth-server.xml
   * @return The ACS url and binding for the specified binding. If the the binding isn't registered in the
   * metadata the ACS url for the default binding is returned.
   */
  /*
  private ACS getACSForBinding(EntityManager manager, String entityID,
                               String requestBinding, String defaultResponseBinding) {
    SPMetadata metadata = (SPMetadata)manager.getMetadata(entityID);
    String acsURL = null;
    EntityDescriptorType saml2Metadata = (EntityDescriptorType)metadata.getPrivateData();
    IndexedEndpointType[] acss = saml2Metadata.getSPSSODescriptorArray(0).getAssertionConsumerServiceArray();
    String defaultAcsURL = null;

    for (IndexedEndpointType acs : acss) {
      if (acs.getBinding().equalsIgnoreCase(requestBinding)) {
        acsURL = acs.getLocation();
      }
      // Find the default binding endpoint in case we need it
      if (acs.getBinding().equalsIgnoreCase(defaultResponseBinding)) {
        defaultAcsURL = acs.getLocation();
      }
    }

    // If there's no Response endpoint binding to match the binding used for the Request, use the default
    ACS acs = new ACS();
    if (acsURL == null) {
      acs.acsURL = defaultAcsURL;
      acs.binding = defaultResponseBinding;
    }
    else {
      acs.acsURL = acsURL;
      acs.binding = requestBinding;
    }

    return acs;
  }
  */

  /**
   * For use with getACSForBinding
   */
//  private class ACS {
//    /** The Attribute Consumer URL */
//    public String acsURL = null;
//    /** The SAML2 protocol binding to be used with acsURL */
//    public String binding = null;
//  }

  // Setters
  public void setDefaultResponseBinding(String defaultResponseBinding) { this.defaultResponseBinding = defaultResponseBinding; }
}
