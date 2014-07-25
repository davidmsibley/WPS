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

import com.google.common.base.Joiner;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import org.apache.commons.io.IOUtils;
import org.n52.wps.commons.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author isuftin (Ivan Suftin, USGS)
 * @author dmsibley (David M Sibley, USGS)
 */
public class PostgresDatabase extends AbstractDatabase {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PostgresDatabase.class);
	private static PostgresDatabase db;
	private static final String KEY_DATABASE_PATH = "path";
	private static final String DEFAULT_DATABASE_PATH
			= Joiner.on(File.separator).join(
					System.getProperty("java.io.tmpdir", "."),
					"Database",
					"Results");
	private static final String KEY_RESULTS_IN_DB = "saveResultsToDb";
	private static final Boolean DEFAULT_RESULTS_IN_DB = Boolean.FALSE;
	public static final String pgCreationString = "CREATE TABLE RESULTS ("
			+ "REQUEST_ID VARCHAR(100) NOT NULL PRIMARY KEY, "
			+ "REQUEST_DATE TIMESTAMP, "
			+ "RESPONSE_TYPE VARCHAR(100), "
			+ "RESPONSE TEXT, "
			+ "RESPONSE_MIMETYPE VARCHAR(100))";
	
	protected boolean isShutdown = false;
	protected final File baseResultDirectory;
	private final Collection<Integer> openConnections;
	
	private PostgresDatabase() {
		isShutdown = false;
		
		File resultDirectory = null;
		try {
			Class.forName("org.postgresql.Driver");
			
			try {
				PropertyUtil propertyUtil = DatabaseUtil.getDatabasePropertyUtil();
				
				if (!propertyUtil.extractBoolean(KEY_RESULTS_IN_DB, DEFAULT_RESULTS_IN_DB)) {
					String baseResultDirectoryPath = propertyUtil.extractString(KEY_DATABASE_PATH, DEFAULT_DATABASE_PATH);
					resultDirectory = new File(baseResultDirectoryPath);
					LOGGER.info("Using \"{}\" as base directory for result", baseResultDirectoryPath);
					if (!resultDirectory.exists()) {
						LOGGER.info("Results storage does not exist, creating {}.", baseResultDirectoryPath);
						resultDirectory.mkdirs();
					}
				} else {
					resultDirectory = null;
					LOGGER.info("Using database for result storage");
				}
				
			} catch (Exception e) {
				isShutdown = true;
				LOGGER.error("Instantiating database failed.");
			}
		} catch (ClassNotFoundException cnf_ex) {
			isShutdown = true;
			LOGGER.error("Database class could not be loaded", cnf_ex);
			throw new UnsupportedDatabaseException("The database class could not be loaded.");
		}
		
		baseResultDirectory = resultDirectory;
		openConnections = Collections.synchronizedSet(new HashSet<Integer>());
	}
	
	private void initialize() {
		if (!isShutdown) {
			Connection con = null;
			try {
				con = getConnection();
				if (null != con) {
					createResultTableIfNotExists(con);
				}
			} catch (Exception e) {
				isShutdown = true;
				LOGGER.error("Could not initialize database.");
			} finally {
				closeConnection(con);
			}
		}
	}
	
	public static PostgresDatabase getInstance() {
		if (PostgresDatabase.db == null) {
			PostgresDatabase.db = new PostgresDatabase();
			PostgresDatabase.db.initialize();
		}
		
		return PostgresDatabase.db;
	}
	
	@Override
	protected synchronized String insertResultEntity(InputStream stream, String id, String type, String mimeType) {
		Boolean storingOutput = DatabaseUtil.isIDForOutput(id);
		
		Path filePath = null;
		Connection con = null;
		try {
			if (null != baseResultDirectory) {
				String filename = storingOutput ? id : UUID.randomUUID().toString();
				filePath = new File(baseResultDirectory, filename).toPath();
				filePath = Files.createFile(filePath);
				Files.copy(stream, filePath, StandardCopyOption.REPLACE_EXISTING);
			}
			
			con = getConnection();
			PreparedStatement ps = con.prepareStatement(AbstractDatabase.insertionString);
			ps.setString(INSERT_COLUMN_REQUEST_ID, id);
			ps.setTimestamp(INSERT_COLUMN_REQUEST_DATE, new Timestamp(System.currentTimeMillis()));
			ps.setString(INSERT_COLUMN_RESPONSE_TYPE, type);
			ps.setString(INSERT_COLUMN_MIME_TYPE, mimeType);

			if (storingOutput && null != filePath) {
				byte[] filePathByteArray = filePath.toUri().toString().getBytes();
				ps.setAsciiStream(INSERT_COLUMN_RESPONSE, new ByteArrayInputStream(filePathByteArray), filePathByteArray.length);
			} else {
				ps.setAsciiStream(INSERT_COLUMN_RESPONSE, stream);
			}

			ps.executeUpdate();
			con.commit();
		} catch (Exception e) {
			LOGGER.error("Could not insert Response into database.", e);
		} finally {
			closeConnection(con);
		}
		
		return generateRetrieveResultURL(id);
	}
	
	@Override
	public synchronized void updateResponse(String id, InputStream stream) {
		Connection con = null;
		try {
			con = getConnection();
			PreparedStatement ps = con.prepareStatement(AbstractDatabase.updateString);
			
			ps.setString(UPDATE_COLUMN_REQUEST_ID, id);
			ps.setAsciiStream(UPDATE_COLUMN_RESPONSE, stream);
			
			ps.executeUpdate();
			getConnection().commit();
		} catch (SQLException e) {
			LOGGER.error("Could not insert Response into database", e);
		} finally {
			closeConnection(con);
		}
	}
	
	@Override
	public synchronized InputStream lookupResponse(String id) {
		InputStream result = null;
		if (null != id) {
			if (DatabaseUtil.isIDForOutput(id) && null != baseResultDirectory) {
				File responseFile = lookupResponseAsFile(id);
				if (responseFile != null && responseFile.exists()) {
					LOGGER.debug("Response file for {} is {}", id, responseFile.getPath());
					try {
						result = responseFile.getName().endsWith(".gz") ? new GZIPInputStream(new FileInputStream(responseFile))
								: new FileInputStream(responseFile);
					} catch (FileNotFoundException e) {
						LOGGER.warn("Response not found for id " + id, e);
					} catch (IOException e) {
						LOGGER.warn("Error processing response for id " + id, e);
					}
				}
				LOGGER.warn("Response not found for id {}", id);
			} else {
				
				Connection con = null;
				try {
					con = getConnection();
					PreparedStatement ps = con.prepareStatement(AbstractDatabase.insertionString);
					ps.setString(SELECT_COLUMN_RESPONSE, id);
					ResultSet res = ps.executeQuery();
					
					if (res == null || !res.next()) {
						LOGGER.warn("Query did not return a valid result.");
					} else {
						LOGGER.info("Successfully retrieved the Response of Request: "
								+ id);
						result = res.getAsciiStream(1);
					}
				} catch (SQLException e) {
					LOGGER.error("SQLException with request_id: " + id, e);
				}
//				result = super.lookupResponse(id); //TODO
			}
		}
		return result;
	}
	
	@Override
	public File lookupResponseAsFile(String id) {
		File result = null;
		
		InputStream responseStream = null;
		try {
			responseStream = super.lookupResponse(id); //TODO
			String fileLocation = IOUtils.toString(responseStream);
			result = new File(fileLocation);
		} catch (Exception e) {
			LOGGER.warn("Could not get file location for response file for id " + id, e);
		} finally {
			IOUtils.closeQuietly(responseStream);
		}

		return result;
	}
	
	private static boolean createResultTableIfNotExists(Connection con) {
		boolean hasResultTable = false;
		
		try {
			ResultSet rs;
			DatabaseMetaData meta = con.getMetaData();
			rs = meta.getTables(null, null, "results", new String[]{"TABLE"});
			if (!rs.next()) {
				LOGGER.info("Table RESULTS does not yet exist.");
				Statement st = con.createStatement();
				
				st.executeUpdate(PostgresDatabase.pgCreationString);
				con.commit();
				
				//Check for the table again
				meta = con.getMetaData();
				rs = meta.getTables(null, null, "results", new String[]{"TABLE"});
				if (rs.next()) {
					LOGGER.info("Succesfully created table RESULTS.");
					hasResultTable = true;
				} else {
					LOGGER.error("Could not create table RESULTS.");
				}
			} else {
				LOGGER.info("Table RESULTS exists.");
				hasResultTable = true;
			}
		} catch (SQLException e) {
			LOGGER.error("Connection to the Postgres database failed: " + e.getMessage());
		}
		
		return hasResultTable;
	}
	
	@Override
	public void shutdown() {
		int ownedCount = openConnections.size();
		if (0 < ownedCount) {
			LOGGER.error("Database still has owned connections while shutting down. Check for missing closeConnection.");
		}
		isShutdown = true;
		PostgresDatabase.db = null;
		System.gc();
		LOGGER.info("Postgres database connection is closed succesfully");
	}
	
	@Override
	protected final Connection getConnection() {
		Connection result = null;
		
		if (!isShutdown) {
			result = DatabaseUtil.createConnection(getConnectionURL());
			if (null != result) {
				int id = result.hashCode();
				boolean wasAdded = openConnections.add(id);
				if (wasAdded) {
					LOGGER.trace("Grabbed connection {}, Owned Count: {}", id, openConnections.size());
				} else {
					LOGGER.error("Grabbed previously owned connection {}! Owned Count: {}", id, openConnections.size());
				}

				try {
					result.setAutoCommit(false);
				} catch (SQLException e) {
					LOGGER.error("Could not set auto commit on {}", id);
				}
			}
		} else {
			LOGGER.trace("Database is shutdown, no allocation of connections allowed.");
		}
		
		return result;
	}
	
	@Override
	protected final void closeConnection(Connection con) {
		if (isShutdown) {
			LOGGER.error("Database is shutdown, you shouldn't have a handle to it.");
		}
		if (null != con) {
			int id = con.hashCode();
			try {
				con.setAutoCommit(true);
			} catch (SQLException e) {
				LOGGER.error("Could not reset auto commit on {}", id);
			}
			
			try {
				con.close();
			} catch (SQLException e) {
				LOGGER.error("Could not close connection {}", id);
			}
			
			boolean wasOpen = openConnections.remove(id);
			if (wasOpen) {
				LOGGER.trace("Closed connection {}, Owned Count: {}", id, openConnections.size());
			} else {
				LOGGER.error("Closed unowned connection {}! Owned Count: {}", id, openConnections.size());
			}
		} else {
			LOGGER.debug("Null passed in to closeConnection");
		}
	}
	
	@Override
	protected String getConnectionURL() {
		//TODO I don't think this will give me the right connection URL.
		return "jdbc:postgresql:" + getDatabasePath() + "/" + getDatabaseName();
	}
	
}
