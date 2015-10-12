package com.kisstools.server.http;

import android.content.Context;
import android.text.TextUtils;

import com.kisstools.KissTools;
import com.kisstools.server.R;
import com.kisstools.utils.FileUtil;
import com.kisstools.utils.UrlUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by dawson on 10/11/15.
 */
public class FileHandler implements RequestHandler {

    private String FILE_LINE = "<li><a class='file' href=\"HREF_PATH\">FILE_NAME</a></li>";

    private String FOLDER_LINE = "<li><a href=\"HREF_PATH\">FILE_NAME</a></li>";

    @Override
    public boolean handleRequest(HttpRequest request, HttpResponse response) {
        String filePath = request.query.get("path");
        if (TextUtils.isEmpty(filePath)) {
            filePath = "/";
        }
        File file = new File(filePath);
        if (file.isDirectory()) {
            String content = createFolderContent(filePath);
            response.header.put("Content-Type", "text/html");
            response.setBody(content);
        } else {
            try {
                createFileContent(filePath, response);
            } catch (FileNotFoundException e) {
                return false;
            }
        }

        response.status = HttpStatus.OK;
        return true;
    }

    private void createFileContent(String filePath, HttpResponse response) throws FileNotFoundException {
        long contentLength = FileUtil.size(filePath);
        response.header.put("Content-Length", "" + contentLength);
        String mimeType = FileUtil.getMimeType(filePath);
        if (TextUtils.isEmpty(mimeType) || "*/*".equals(mimeType)) {
            mimeType = "application/octet-stream";
            String fileName = FileUtil.getName(filePath);
            fileName = "attachment; filename=\"" + fileName + "\"";
            response.header.put("Content-Disposition", fileName);
        }
        response.header.put("Content-Type", mimeType);
        response.body = new FileInputStream(filePath);
    }

    private String createFolderContent(String filePath) {
        Context context = KissTools.getApplicationContext();
        InputStream is = context.getResources().openRawResource(R.raw.content);
        String content = FileUtil.read(is);
        content = content.replace("FILE_PATH", filePath);
        String dataPath = UrlUtil.encode(context.getApplicationInfo().dataDir);
        content = content.replace("DATA_PATH", dataPath);

        String text = "";
        String parentPath = FileUtil.getParent(filePath);
        if (parentPath != null && filePath.length() > 1) {
            String hrefPath = "/file?path=" + UrlUtil.encode(parentPath);
            text = FOLDER_LINE.replace("HREF_PATH", hrefPath).replace("FILE_NAME", "..");
        }
        File[] children = new File(filePath).listFiles();
        if (children != null && children.length > 0) {
            List<File> childList = sortFolderContent(children);
            for (int index = 0; index < children.length; ++index) {
                String prefix = filePath.equals("/") ? "" : filePath;
                File childFile = childList.get(index);
                String childName = childFile.getName();
                String childPath = prefix + "/" + childName;
                childName = childFile.isDirectory() ? " " + childName : childName;
                String hrefPath = "/file?path=" + UrlUtil.encode(childPath);
                String line = FOLDER_LINE;
                if (!childFile.isDirectory()) {
                    line = FILE_LINE;
                }
                line = line.replace("HREF_PATH", hrefPath).replace("FILE_NAME", childName);
                text = text + "\n" + line;
            }
        }
        content = content.replace("CONTENT", text);
        return content;
    }

    private List<File> sortFolderContent(File[] list) {
        List<File> fileList = new ArrayList<>();
        List<File> folderList = new ArrayList<>();
        for (File file : list) {
            if (file.isDirectory()) {
                folderList.add(file);
            } else {
                fileList.add(file);
            }
        }

        sortFileByName(fileList);
        sortFileByName(folderList);
        folderList.addAll(fileList);
        return folderList;
    }

    private void sortFileByName(List<File> fileList) {
        final Collator collator = Collator.getInstance();
        Collections.sort(fileList, new Comparator<File>() {
            @Override
            public int compare(File l, File r) {
                return collator.compare(l.getName(), r.getName());
            }
        });
    }
}
