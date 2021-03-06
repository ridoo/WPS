/**
 * Copyright (C) 2006-2015 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.n52.wps.webapp.common;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.n52.wps.commons.WPSConfig;
import org.n52.wps.webapp.api.ConfigurationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath*:applicationContext.xml",
		"classpath*:dispatcher-servlet.xml"})
@WebAppConfiguration(value = "src/test/webapp")
@ActiveProfiles(profiles = { "test", "common-test" })
public class AbstractITClass {
	@Autowired
	protected WebApplicationContext wac;

    @Before
    public void setup() {
        MockMvcBuilders.webAppContextSetup(this.wac).build();
		WPSConfig.getInstance().setConfigurationManager(this.wac.getBean(ConfigurationManager.class));
    }
}
