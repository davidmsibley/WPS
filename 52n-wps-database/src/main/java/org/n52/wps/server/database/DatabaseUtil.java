/**
 * ﻿Copyright (C) 2007 - 2014 52°North Initiative for Geospatial Open Source
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
package org.n52.wps.server.database;

import com.google.common.base.Strings;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import org.n52.wps.DatabaseDocument;
import org.n52.wps.commons.PropertyUtil;
import org.n52.wps.commons.WPSConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author dmsibley
 */
public class DatabaseUtil {
	private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseUtil.class);
	private static final String KEY_DATABASE_ROOT = "org.n52.wps.server.database";

	public static PropertyUtil getDatabasePropertyUtil() {
		DatabaseDocument.Database databaseDoc = WPSConfig.getInstance().getWPSConfig().getServer().getDatabase();
		PropertyUtil propertyUtil = new PropertyUtil(databaseDoc.getPropertyArray(), KEY_DATABASE_ROOT);
		return propertyUtil;
	}
	
	public static boolean isIDForOutput(String id) {
		boolean result = false;
		if (null != id && id.toLowerCase().contains("output")) {
			result = true;
		}
		return result;
	}
	
	public static Connection createConnection(String connectionURL) {
		Connection result = null;
		Properties props = new Properties();
		DataSource dataSource;
		
		PropertyUtil prop = getDatabasePropertyUtil();
		String jndiName = prop.extractString("jndiName", null);
		String username = prop.extractString("username", null);
		String password = prop.extractString("password", null);

		if (!Strings.isNullOrEmpty(jndiName)) {
			InitialContext context;
			try {
				context = new InitialContext();
				dataSource = (DataSource) context.lookup("java:comp/env/jdbc/" + jndiName);
				result = dataSource.getConnection();
				LOGGER.info("Connected to WPS database.");
			} catch (NamingException e) {
				LOGGER.error("Could not connect to or create the database.", e);
			} catch (SQLException e) {
				LOGGER.error("Could not connect to or create the database.", e);
			}
		} else if (!Strings.isNullOrEmpty(username) 
				&& !Strings.isNullOrEmpty(password) 
				&& !Strings.isNullOrEmpty(connectionURL)){
			props.setProperty("user", username);
			props.setProperty("password", password);
			try {
				result = DriverManager.getConnection(connectionURL, props);
				LOGGER.info("Connected to WPS database.");
			} catch (SQLException e) {
				LOGGER.error("Could not connect to or create the database.", e);
			}
		} else {
			LOGGER.error("Could not connect to or create the database.  Missing configuration.");
		}
		return result;
	}
}
