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

package org.guanxi.idp;

import org.springframework.mock.web.MockServletContext;
import org.junit.BeforeClass;
import org.guanxi.common.GuanxiPrincipal;
import org.guanxi.common.Utils;

import java.util.ResourceBundle;

/**
 * The root of the IdP test hierarchy
 */
public abstract class IdPTest {
  protected static MockServletContext servletContext = null;
  protected static GuanxiPrincipal principal = null;
  protected static ResourceBundle idpProperties = null;

  protected static final String TEST_USER_NAME = "harrymcd";
  protected static final String TEST_USER_EMAIL = "harrymcd@jumpingupanddown.net";
  protected static final String TEST_RELYING_PARTY = "protectedapp-guard";

  @BeforeClass
  public static void initIdPTest() {
    idpProperties = ResourceBundle.getBundle("test");
    servletContext = new MockServletContext(Paths.path("servlet.context.home"));

    principal = new GuanxiPrincipal();
    principal.setName(TEST_USER_NAME);
    principal.getPrivateProfileData().put("username", TEST_USER_NAME);
    principal.setUniqueId(Utils.getUniqueID());
  }
}
