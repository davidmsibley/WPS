INSERT INTO CONFIGURATIONMODULE VALUES('org.n52.wps.webapp.testmodules.TestConfigurationModule1', 'true')
INSERT INTO CONFIGURATIONMODULE VALUES('org.n52.wps.webapp.testmodules.TestConfigurationModule2', 'false')
INSERT INTO CONFIGURATIONENTRY VALUES('test.boolean.key','org.n52.wps.webapp.testmodules.TestConfigurationModule1','true')
INSERT INTO CONFIGURATIONENTRY VALUES('test.double.key','org.n52.wps.webapp.testmodules.TestConfigurationModule1','11.3')
INSERT INTO CONFIGURATIONENTRY VALUES('test.file.key','org.n52.wps.webapp.testmodules.TestConfigurationModule1','test_path')
INSERT INTO CONFIGURATIONENTRY VALUES('test.integer.key','org.n52.wps.webapp.testmodules.TestConfigurationModule1','23')
INSERT INTO CONFIGURATIONENTRY VALUES('test.string.key','org.n52.wps.webapp.testmodules.TestConfigurationModule1','Test Value')
INSERT INTO CONFIGURATIONENTRY VALUES('test.uri.key','org.n52.wps.webapp.testmodules.TestConfigurationModule1','test_path')
INSERT INTO ALGORITHMENTRY VALUES('name1','org.n52.wps.webapp.testmodules.TestConfigurationModule1',TRUE)
INSERT INTO ALGORITHMENTRY VALUES('name2','org.n52.wps.webapp.testmodules.TestConfigurationModule1',TRUE)
INSERT INTO USERS VALUES(1,'testUser1','1388094c29b6e2999b09e28ee366a01c3e266bb28b1069a5ef073c97af2d25b32103d43c8860fd88', 'ROLE_ADMIN')
INSERT INTO USERS VALUES(2,'testUser2','1388094c29b6e2999b09e28ee366a01c3e266bb28b1069a5ef073c97af2d25b32103d43c8860fd88', 'ROLE_USER')