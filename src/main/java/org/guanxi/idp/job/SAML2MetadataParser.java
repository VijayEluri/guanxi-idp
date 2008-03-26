/* CVS Header
   $
   $
*/

package org.guanxi.idp.job;

import org.quartz.Job;
import org.quartz.JobExecutionException;
import org.quartz.JobExecutionContext;
import org.guanxi.idp.Bootstrap;
import org.guanxi.xal.saml_2_0.metadata.EntityDescriptorType;
import org.guanxi.xal.saml_2_0.metadata.EntitiesDescriptorDocument;
import org.guanxi.xal.saml_2_0.metadata.SPSSODescriptorType;
import org.guanxi.common.Utils;
import org.guanxi.common.GuanxiException;
import org.apache.xmlbeans.XmlException;

import java.net.URL;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;

/**
 * Parses the UK Federation metadata
 */
public class SAML2MetadataParser implements Job {
  public SAML2MetadataParser() {
    super();
  }

  public void execute(JobExecutionContext context) throws JobExecutionException {
    // Get our custom config
    SAML2MetadataParserConfig config = (SAML2MetadataParserConfig)context.getJobDetail().getJobDataMap().get(Bootstrap.JOB_KEY_JOB_CONFIG);

    try {
      EntitiesDescriptorDocument doc = Utils.parseSAML2Metadata(config.getMetadataURL(), config.getWho());
      EntityDescriptorType[] entityDescriptors = doc.getEntitiesDescriptor().getEntityDescriptorArray();

      for (EntityDescriptorType entityDescriptor : entityDescriptors) {
        // Look for Service Providers
        if (entityDescriptor.getSPSSODescriptorArray().length > 0) {
          config.getLog().info("Loading SP metadata for : " + entityDescriptor.getEntityID());
          config.getServletContext().setAttribute(entityDescriptor.getEntityID(), entityDescriptor);
        }
      }
    }
    catch(GuanxiException ge) {
      config.getLog().error("Error parsing metadata", ge);
    }
  }
}
