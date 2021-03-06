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

package org.guanxi.idp.service;

import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

public class UrlRewriter extends HandlerInterceptorAdapter implements ServletContextAware {
  /** The ServletContext, passed to us by Spring as we are ServletContextAware */
  private ServletContext servletContext = null;
  /** Our logger */
  private static final Logger logger = Logger.getLogger(UrlRewriter.class.getName());
  /** The URLs to map */
  private HashMap<?, ?> urlMaps = null;

  /**
   * Initialise the interceptor
   */
  public void init() {
  }

  /**
   * Rewrites an incoming URL based on the rules defined in services/url-rewriter.xml
   * If you want to map /SSO to /shibb/sso, do this:
   * -- in web.xml, add a servlet-mapping for /SSO to the Guanxi Identity Provider servlet
   * -- in services/url-rewriter.xml, add <entry key="SSO" value="/shibb/sso" />, omit the leading /
   *
   * @param request Standard HttpServletRequest
   * @param response Standard HttpServletResponse
   * @param object handler
   * @return true
   * @throws Exception if an error occurs
   */
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object object) throws Exception {
    String[] parts = request.getRequestURI().split("/");
    if (urlMaps.containsKey(parts[parts.length-1])) {
      request.getRequestDispatcher((String)urlMaps.get(parts[parts.length-1])).forward(request, response);
      return false;
    }
    return true;
  }

  // Called by Spring as we are ServletContextAware
  public void setServletContext(ServletContext servletContext) { this.servletContext = servletContext; }

  public void setUrlMaps(HashMap<?, ?> urlMaps) { this.urlMaps = urlMaps; }
}
