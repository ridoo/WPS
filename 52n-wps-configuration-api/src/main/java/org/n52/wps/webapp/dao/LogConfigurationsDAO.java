/**
 * Copyright (C) 2007-2015 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *       • Apache License, version 2.0
 *       • Apache Software License, version 1.0
 *       • GNU Lesser General Public License, version 3
 *       • Mozilla Public License, versions 1.0, 1.1 and 2.0
 *       • Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.wps.webapp.dao;

import org.n52.wps.webapp.entities.LogConfigurations;

/**
 * Read and write to/from the log configuration file to/from {@link LogConfigurations} object.
 */
public interface LogConfigurationsDAO {
	/**
	 * Get the log configuration file and parse it to a {@code LogConfigurations} object
	 * 
	 * @return Log configurations object
	 */
	LogConfigurations getLogConfigurations();

	/**
	 * Write a {@code LogConfigurations} object to log file
	 * 
	 * @param logConfigurations
	 *            the log configurations object
	 */
	void saveLogConfigurations(LogConfigurations logConfigurations);
}
