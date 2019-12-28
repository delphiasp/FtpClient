package com.hwang.common.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

public class FtpUtil {
	public FTPClient ftpClient = new FTPClient();
	private String temp_OP;
	private String temp_ED;
	private String passiveMode;
	private String encode;
	protected final transient Log logger = LogFactory.getLog(FtpUtil.class);

	public FtpUtil() {
		this.temp_ED = "";
		this.temp_OP = "";
		this.ftpClient.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
	}

	public FtpUtil(String op, String ed) {
		this.temp_OP = op;
		this.temp_ED = ed;
		this.ftpClient.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
	}

	public boolean connect(String hostname, int port, String username, String password, String passiveMode,
			String encodeStr) throws IOException {
		this.passiveMode = passiveMode;
		this.encode = encodeStr;
		ftpClient.connect(hostname, port);
		ftpClient.setControlEncoding(encode);
		if (FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
			if (ftpClient.login(username, password)) {
				return true;
			}
		}
		disconnect();
		return false;
	}

	public String getPassiveMode() {
		return passiveMode;
	}

	public void setPassiveMode(String passiveMode) {
		this.passiveMode = passiveMode;
	}

	public String getEncode() {
		return encode;
	}

	public void setEncode(String encode) {
		this.encode = encode;
	}

	public void disconnect() throws IOException {
		if (ftpClient.isConnected()) {
			ftpClient.disconnect();
		}
	}

	public String[] download(String remote, String local, String ftpMode) throws IOException {
		if (passiveMode.equalsIgnoreCase("true"))
			ftpClient.enterLocalPassiveMode();

		ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
		if ("ASCII".equalsIgnoreCase(ftpMode)) {
			ftpClient.setFileType(FTP.ASCII_FILE_TYPE);
		}
		List<String> results = new ArrayList<String>();

		FTPFile[] files = ftpClient.listFiles(new String(remote.getBytes(encode)));

		for (int i = 0; i < files.length; i++) {
			String remoteFile = files[i].getName();
			String localFile = files[i].getName();
			if ((!"".equals(temp_OP) && remoteFile.startsWith(temp_OP))
					|| (!"".equals(temp_ED) && remoteFile.endsWith(temp_ED))) {
				continue;
			}
			DownloadStatus downloadStatus = downloadFile(remote + remoteFile, local + localFile, ftpMode);
			if (downloadStatus == DownloadStatus.Download_From_Break_Success
					|| downloadStatus == DownloadStatus.Download_New_Success) {
				results.add(remoteFile);
			} else {
				logger.error("download " + remoteFile + " failed: " + downloadStatus.toString());
			}
		}
		return results.toArray(new String[results.size()]);
	}

	public DownloadStatus downloadFile(String remote, String local, String ftpMode) throws IOException {
		if (passiveMode.equalsIgnoreCase("true"))
			ftpClient.enterLocalPassiveMode();
		ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
		if ("ASCII".equalsIgnoreCase(ftpMode)) {
			ftpClient.setFileType(FTP.ASCII_FILE_TYPE);
		}
		DownloadStatus result;

		FTPFile[] files = ftpClient.listFiles(new String(remote.getBytes(encode)));
		logger.info("downloadFile: From [" + new String(remote.getBytes(encode)) + "] To [" + local + "] ["
				+ files.length + " ]");
		if (files.length != 1) {
			logger.info("downloadFile: Remote_File_Noexist");
			return DownloadStatus.Remote_File_Noexist;
		}

		long lRemoteSize = files[0].getSize();
		File f = new File(local);
		if (f.exists()) {
			long localSize = f.length();
			if (localSize >= lRemoteSize) {
				logger.info("downloadFile: Local_Bigger_Remote");
				return DownloadStatus.Local_Bigger_Remote;
			}

			FileOutputStream out = null;
			ftpClient.setRestartOffset(localSize);
			InputStream in = ftpClient.retrieveFileStream(new String(remote.getBytes(encode)));
			byte[] bytes = new byte[1024];
			long step = lRemoteSize / 100;
			long process = localSize / step;
			int c;
			try {
				out = new FileOutputStream(f, true);
				while ((c = in.read(bytes)) != -1) {
					out.write(bytes, 0, c);
					localSize += c;
					long nowProcess = localSize / step;
					if (nowProcess > process) {
						process = nowProcess;
						if (process % 50 == 0)
							logger.info("downloadFile process: " + process);
					}
				}
			} catch (IOException ex) {
				logger.error(ex.getMessage());
			} finally {
				try {
					if (out != null)
						out.close();
					if (in != null)
						in.close();
				} catch (IOException ex) {
					System.out.println(ex.getMessage());
				}

			}

			boolean isDo = ftpClient.completePendingCommand();
			if (isDo) {
				logger.info("downloadFile: Download_From_Break_Success");
				result = DownloadStatus.Download_From_Break_Success;
			} else {
				logger.info("downloadFile: Download_From_Break_Failed");
				result = DownloadStatus.Download_From_Break_Failed;
			}
		} else {
			OutputStream out = null;
			InputStream in = null;
			try {
				out = new FileOutputStream(f);
				in = ftpClient.retrieveFileStream(new String(remote.getBytes(encode)));
				byte[] bytes = new byte[1024];
				long step = lRemoteSize / 100;
				long process = 0;
				long localSize = 0L;
				int c;
				while ((c = in.read(bytes)) != -1) {
					out.write(bytes, 0, c);
					localSize += c;
					long nowProcess = localSize / step;
					if (nowProcess > process) {
						process = nowProcess;
						if (process % 50 == 0)
							logger.info("downloadFile process: " + process);
					}
				}
			} catch (IOException ex) {
				logger.error(ex.getMessage());
			} finally {
				try {
					if (out != null)
						out.close();
					if (in != null)
						in.close();
				} catch (IOException ex) {
					System.out.println(ex.getMessage());
				}

			}

			boolean upNewStatus = ftpClient.completePendingCommand();
			if (upNewStatus) {
				logger.info("downloadFile: Download_New_Success");
				result = DownloadStatus.Download_New_Success;
			} else {
				logger.info("downloadFile: Download_New_Failed");
				result = DownloadStatus.Download_New_Failed;
			}
		}
		return result;
	}

	public UploadStatus upload(String local, String remote, String ftpMode) throws Exception {
		logger.info("upload: From[" + new String(local.getBytes(encode)) + "] To ["
				+ new String(remote.getBytes(encode)) + "]");
		if (passiveMode.equalsIgnoreCase("true"))
			ftpClient.enterLocalPassiveMode();
		ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
		if ("ASCII".equalsIgnoreCase(ftpMode)) {
			ftpClient.setFileType(FTP.ASCII_FILE_TYPE);
		}
		ftpClient.setControlEncoding(encode);
		UploadStatus result;

		String remoteFileName = remote + this.temp_ED;

		if (remote.contains("/")) {
			remoteFileName = remote.substring(remote.lastIndexOf("/") + 1) + this.temp_ED;
			if (createDirecroty(remote, ftpClient) == UploadStatus.Create_Directory_Fail) {
				logger.info("Create directory failed.");
				return UploadStatus.Create_Directory_Fail;
			}
		}

		FTPFile[] filesOld = ftpClient.listFiles(new String((remoteFileName).getBytes(encode)));
		if (filesOld.length == 1) {
			if (!ftpClient.deleteFile((remoteFileName))) {
				logger.info("Delete exists file:[" + remoteFileName + "] failed");
				return UploadStatus.Delete_Remote_Faild;
			} else {
				logger.info("Delete exists file:[" + remoteFileName + "] successfully");
			}
		}

		String newFileName = remoteFileName.replace(this.temp_OP, "").replace(this.temp_ED, "");

		FTPFile[] filesOldF = ftpClient.listFiles(new String((newFileName).getBytes(encode)));
		if (filesOldF.length == 1) {
			if (!ftpClient.deleteFile((newFileName))) {
				logger.info("Delete exists file:[" + newFileName + "] failed");
				return UploadStatus.Delete_Remote_Faild;
			} else {
				logger.info("Delete exists file:[" + newFileName + "] successfully");
			}
		}

		logger.info("uploading..." + remoteFileName);
		result = uploadFile(remoteFileName, new File(local), ftpClient, 0);
		if (result.equals(UploadStatus.Upload_New_File_Success)) {
			logger.info("upload[" + remoteFileName + "] successfully");
		} else {
			logger.info("upload[" + remoteFileName + "] failed");
		}

		if (!remoteFileName.equals(newFileName) && (result.equals(UploadStatus.Upload_New_File_Success)
				|| result.equals(UploadStatus.Upload_From_Break_Success))) {
			logger.info("Rename file :" + remoteFileName + " to " + newFileName);
			result = this.alterFileName(remoteFileName, newFileName);
			if (result.equals(UploadStatus.Upload_New_File_Success)) {
				logger.info("Rename file to [" + newFileName + "] successfully");
			} else {
				logger.info("Rename file to [" + newFileName + "] failed");
			}
		}
		return result;
	}

	public UploadStatus createDirecroty(String remote, FTPClient ftpClient) throws IOException {
		UploadStatus status = UploadStatus.Create_Directory_Success;
		String directory = remote.substring(0, remote.lastIndexOf("/") + 1);
		if (!directory.equalsIgnoreCase("/")
				&& !ftpClient.changeWorkingDirectory(new String(directory.getBytes(encode)))) {
			int start = 0;
			int end = 0;
			if (directory.startsWith("/")) {
				start = 1;
			} else {
				start = 0;
			}
			end = directory.indexOf("/", start);
			while (true) {
				String subDirectory = new String(remote.substring(start, end).getBytes(encode));
				if (!ftpClient.changeWorkingDirectory(subDirectory)) {
					if (ftpClient.makeDirectory(subDirectory)) {
						ftpClient.changeWorkingDirectory(subDirectory);
					} else {
						logger.info("Create_Directory_Fail");
						return UploadStatus.Create_Directory_Fail;
					}
				}

				start = end + 1;
				end = directory.indexOf("/", start);

				if (end <= start) {
					break;
				}
			}
		}
		return status;
	}

	public UploadStatus uploadFile(String remoteFile, File localFile, FTPClient ftpClient, long remoteSize)
			throws IOException {
		UploadStatus status;
		double step = localFile.length() / 100.0;
		double process = 0;
		double localreadbytes = 0L;
		RandomAccessFile raf = new RandomAccessFile(localFile, "r");
		OutputStream out = ftpClient.appendFileStream(new String(remoteFile.getBytes(encode)));
		if (remoteSize > 0) {
			ftpClient.setRestartOffset(remoteSize);
			process = remoteSize / step;
			raf.seek(remoteSize);
			localreadbytes = remoteSize;
		}
		byte[] bytes = new byte[1024];
		int c;
		while ((c = raf.read(bytes)) != -1) {
			out.write(bytes, 0, c);
			localreadbytes += c;
			if ((localreadbytes / step - process) != 0) {
				process = localreadbytes / step;
				if (process % 50 == 0)
					logger.info("process:" + (int) process + "%");
			}
		}
		out.flush();
		raf.close();
		out.close();
		boolean result = ftpClient.completePendingCommand();
		if (remoteSize > 0) {
			status = result ? UploadStatus.Upload_From_Break_Success : UploadStatus.Upload_From_Break_Failed;
			if (result) {
				logger.info("Upload_From_Break_Success");
			} else {
				logger.info("Upload_From_Break_Failed");
			}
		} else {
			status = result ? UploadStatus.Upload_New_File_Success : UploadStatus.Upload_New_File_Failed;
			if (result) {
				logger.info("Upload_New_File_Success");
			} else {
				logger.info("Upload_New_File_Failed");
			}
		}
		return status;
	}

	public enum UploadStatus {
		Fail, Create_Directory_Fail, Create_Directory_Success, Upload_New_File_Success, Upload_New_File_Failed,
		File_Exits, Remote_Bigger_Local, Upload_From_Break_Success, Upload_From_Break_Failed, Delete_Remote_Faild,
		Empty_File_Upload;
	}

	public enum DownloadStatus {
		Remote_File_Noexist, Local_Bigger_Remote, Download_From_Break_Success, Download_From_Break_Failed,
		Download_New_Success, Download_New_Failed;
	}

	public boolean deleteFile(String pathName) throws IOException {
		return ftpClient.deleteFile(pathName);
	}

	public UploadStatus alterFileName(String oldName, String newName) throws Exception {
		FTPFile[] files = ftpClient.listFiles(new String(newName.getBytes(encode)));
		if (files.length == 1) {
			String newRename = newName + "new";
			ftpClient.rename(new String(newName.getBytes(encode)), new String(newRename.getBytes(encode)));
			ftpClient.rename(new String(oldName.getBytes(encode)), new String(newName.getBytes(encode)));
			deleteFile(newRename);

		} else if (ftpClient.rename(new String(oldName.getBytes(encode)), new String(newName.getBytes(encode))))
			return UploadStatus.Upload_New_File_Success;

		return UploadStatus.Fail;
	}

	public boolean changeDirectory(String path) throws IOException {
		return ftpClient.changeWorkingDirectory(path);
	}

	public boolean createDirectory(String pathName) throws IOException {
		return ftpClient.makeDirectory(pathName);
	}

	public boolean removeDirectory(String path) throws IOException {
		return ftpClient.removeDirectory(path);
	}

	public boolean removeDirectory(String path, boolean isAll) throws IOException {
		if (!isAll) {
			return removeDirectory(path);
		}
		FTPFile[] ftpFileArr = ftpClient.listFiles(path);
		if (ftpFileArr == null || ftpFileArr.length == 0) {
			return removeDirectory(path);
		}
		for (FTPFile ftpFile : ftpFileArr) {
			String name = ftpFile.getName();
			if (ftpFile.isDirectory()) {
				logger.info("Remove directory[" + path + "/" + name + "]");
				removeDirectory(path + "/" + name, true);
			} else {
				logger.info("Remove File[" + path + "/" + name + "]");
				deleteFile(path + "/" + name);
			}
		}
		return ftpClient.removeDirectory(path);
	}

	public boolean existDirectory(String path) throws IOException {
		boolean flag = false;
		FTPFile[] ftpFileArr = ftpClient.listFiles(path);
		for (FTPFile ftpFile : ftpFileArr) {
			if (ftpFile.isDirectory() && ftpFile.getName().equalsIgnoreCase(path)) {
				flag = true;
				break;
			}
		}
		return flag;
	}

	public boolean existFile(String remotePath) {
		boolean flag = false;
		try {
			FTPFile[] files = ftpClient.listFiles(new String(remotePath.getBytes(encode)));
			if (files.length == 1) {
				flag = true;
				logger.info("Remote exists " + remotePath);
			} else {
				logger.info("Remote not exists" + remotePath);
			}
		} catch (Exception e) {
			logger.info(e.getMessage(), e);
		}
		return flag;
	}

	public String getTemp_OP() {
		return temp_OP;
	}

	public void setTemp_OP(String temp_OP) {
		this.temp_OP = temp_OP;
	}

	public String getTemp_ED() {
		return temp_ED;
	}

	public void setTemp_ED(String temp_ED) {
		this.temp_ED = temp_ED;
	}

	public static void main(String[] args) {
		FtpUtil myFtp = new FtpUtil("", ".tmp");
		try {
			myFtp.connect("10.10.10.131", 21, "test", "test", "false", "GBK");
			myFtp.upload("e:\\apache-tomcat-8.5.30.zip", "/home/test/test/awd.txt", "");
			myFtp.disconnect();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e.getMessage());
		}
	}

}
