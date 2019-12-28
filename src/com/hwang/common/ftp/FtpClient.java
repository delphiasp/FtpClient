package com.hwang.common.ftp;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.hwang.common.util.DES;
import com.hwang.common.util.FtpUtil;

public class FtpClient {
	private String CFGFILE = "ftp.properties";
	private String server_ip = "";
	private String user_name, user_pass, passiveMode, encode;

	static Logger log;

	private void init_ftp() throws Exception {

		System.setProperty("WORK_DIR", System.getProperty("user.dir"));

		PropertyConfigurator.configure(
				System.getProperty("user.dir") + File.separator + "config" + File.separator + "log4j.properties");

		System.out.println(
				System.getProperty("user.dir") + File.separator + "config" + File.separator + "log4j.properties");

		log = Logger.getLogger(FtpClient.class.getName());

		log.info("FTP配置文件: "
				+ System.getProperty("user.dir") + File.separator + "config" + File.separator +CFGFILE);
		DES des = new DES();
		Properties prop = new Properties();
		FileInputStream inputFile = new FileInputStream(
				System.getProperty("user.dir") + File.separator + "config" + File.separator +CFGFILE);
		
		prop.load(inputFile);

		server_ip = prop.getProperty("server_ip");
		user_name = des.decrypt(prop.getProperty("user_name"));
		user_pass = des.decrypt(prop.getProperty("user_pass"));
		passiveMode = prop.getProperty("passiveMode");
		encode = prop.getProperty("encode");

		inputFile.close();

		log.info("server_ip:" + server_ip);
		log.info("user_name:" + user_name);
		log.info("passiveMode:" + passiveMode);
		log.info("encode:" + encode);

	}


	public boolean FtpUploadFile(String fileLocal, String fileRemote, String ftpMode) throws Exception {
		init_ftp();
		FtpUtil ftp = new FtpUtil("", ".tmp");
		try {

			// ftp至服务器
			ftp.connect(server_ip, 21, user_name, user_pass, passiveMode, encode);
			ftp.upload(fileLocal, fileRemote, ftpMode);
			ftp.disconnect();
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e.getMessage());
			ftp.disconnect();
			return false;
		}
		return true;
	}

	public boolean FtpDownloadFile(String fileLocal, String fileRemote, String ftpMode) throws Exception {
		init_ftp();
		FtpUtil ftp = new FtpUtil("", ".tmp");
		try {

			// ftp至服务器
			ftp.connect(server_ip, 21, user_name, user_pass, passiveMode, encode);
			ftp.downloadFile(fileRemote, fileLocal, ftpMode);
			ftp.disconnect();
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e.getMessage());
			ftp.disconnect();
			return false;
		}
		return true;
	}
	public static void main(String args[]) throws Exception {
		Logger log;
		log = Logger.getLogger(FtpClient.class.getName());
		try {
			FtpClient ftpclient = new FtpClient();

			if (args.length < 4) {
				System.out.println("parameters invalid,need 4 parameters");
				System.out.println(ftpclient.getClass().getName() + " Upload/Download ASCII/BINARY LocalFileName RemoteFileName");
				return;
			}

			ftpclient.init_ftp();
			if (args[0].equalsIgnoreCase("UPLOAD")) {
				ftpclient.FtpUploadFile(args[2], args[3], args[1]);
			}

			if (args[0].equalsIgnoreCase("DOWNLOAD")) {
				ftpclient.FtpDownloadFile(args[2], args[3], args[1]);
			}
			
			log.info("Ftp to server successfully.");
			System.exit(0);
		} catch (Exception e) {
			log.info("Ftp to server failed.");
			e.printStackTrace();
			log.info(e);
			System.exit(1);
		}
	}

}
