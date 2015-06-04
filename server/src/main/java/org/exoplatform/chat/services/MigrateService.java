package org.exoplatform.chat.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.chat.utils.PropertyManager;

public class MigrateService {

  private static final Logger LOG = LoggerFactory.getLogger(MigrateService.class);

  public MigrateService() {}

  public void migrate() {
    // Collect database info
    String hostname = PropertyManager.getProperty(PropertyManager.PROPERTY_SERVER_HOST);
    String port = PropertyManager.getProperty(PropertyManager.PROPERTY_SERVER_PORT);
    String dbName = PropertyManager.getProperty(PropertyManager.PROPERTY_DB_NAME);
    String isAuth = PropertyManager.getProperty(PropertyManager.PROPERTY_DB_AUTHENTICATION);
    String username = "", password = "";
    if (Boolean.parseBoolean(isAuth)) {
      username = PropertyManager.getProperty(PropertyManager.PROPERTY_DB_USER);
      password = PropertyManager.getProperty(PropertyManager.PROPERTY_DB_PASSWORD);
    }

    if (StringUtils.isEmpty(dbName)) {
      LOG.error("Database name is required. Set it in the variable 'dbName' in chat.properties");
      return;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("mongo --quiet ");
    if (!StringUtils.isEmpty(hostname)) {
      sb.append(hostname);
      if (!StringUtils.isEmpty(port)) {
        sb.append(":")
          .append(port);
      }
      sb.append("/");
    }

    sb.append(dbName)
      .append(" ");

    if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
      sb.append("-u ")
        .append(username)
        .append(" -p ")
        .append(password)
        .append(" ");
    }

    // Copy migration script to /temp folder to perform migrate process via mongo command
    InputStream fileIn = this.getClass().getClassLoader().getResourceAsStream("migration-chat-addon.js");
    OutputStream fileOut = null;
    String migrationScriptPath = "";

    String tomcatHomeDir = System.getProperty("catalina.base");
    String jbossHomeDir = System.getProperty("jboss.home.dir");
    if (!StringUtils.isEmpty(tomcatHomeDir)) {
      migrationScriptPath += tomcatHomeDir + "/temp/migration-chat-addon.js";
    } else if (!StringUtils.isEmpty(jbossHomeDir)) {
      migrationScriptPath += jbossHomeDir + "/temp/migration-chat-addon.js";
    }

    File migrationScriptfile = new File(migrationScriptPath);
    try {
      if (migrationScriptfile.createNewFile()) {
        fileOut = new FileOutputStream(migrationScriptfile);
        byte[] buf = new byte[1024];
        int bytesRead;
        while ((bytesRead = fileIn.read(buf)) > 0) {
          fileOut.write(buf, 0, bytesRead);
        }
      }
    } catch(IOException e){
      LOG.error("Failed to copy file : "+e.getMessage(), e);
    } finally {
      try {
        fileIn.close();
        fileOut.close();
      } catch (IOException e){
        LOG.error("Failed to close files : "+e.getMessage(), e);
      }
    }

    // Execute mongo command
    String command = sb.append(migrationScriptPath).toString();
    StringBuffer output = new StringBuffer();
    Process p;
    try {
      p = Runtime.getRuntime().exec(command);
      p.waitFor();
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
      String line = "";
      while ((line = reader.readLine())!= null) {
        output.append(line + "\n");
      }
      LOG.info("====== Migration process output ======");
      LOG.info(output.toString());
    } catch (Exception e) {
      LOG.error("Error while migrating chat data : " + e.getMessage(), e);
    } finally {
      if (migrationScriptfile.delete()) {
        LOG.info("Migration script is deleted");
      } else {
        LOG.error("Deleting migration script operation is failed");
      }
    }
  }
}