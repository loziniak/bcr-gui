package com.github.nicorac.plugins.capacitorandroidsaf;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.activity.result.ActivityResult;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;

@CapacitorPlugin(name = "AndroidSAF")
public class AndroidSAFPlugin extends Plugin {

  private static final String ERR_CANCELED = "ERR_CANCELED";
  private static final String ERR_INVALID_URI = "ERR_INVALID_URI";
  private static final String ERR_NOT_FOUND = "ERR_NOT_FOUND";
  private static final String ERR_IO_EXCEPTION = "ERR_IO_EXCEPTION";

  /**
   * Allow client to select a directory and get access to contained files and subfolders
   */
  @PluginMethod()
  public void selectDirectory(PluginCall call) {

    // get input arguments
    String initialUri = call.getString("initialUri", "");

    // open folder selector
    var intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    if (initialUri != "") {
      intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
    }

    // start activity
    startActivityForResult(call, intent, "selectDirectoryResult");

  }

  @ActivityCallback()
  private void selectDirectoryResult(PluginCall call, ActivityResult result) {

    if (call == null) {
      return;
    }

    if (result.getResultCode() != AppCompatActivity.RESULT_OK) {
      call.reject(result.toString(), ERR_CANCELED);
      return;
    }

    // extract intent
    Intent intent = result.getData();
    Uri uri = intent.getData();

    // ask for persistent access
    int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    getContext().getContentResolver().takePersistableUriPermission(uri, takeFlags);

    // Do something with the result data
    var ret = new JSObject();
    ret.put("selectedUri", intent.getDataString());
    call.resolve(ret);

  }

  /**
   * Return an array of filenames contained in the given folder Uri
   */
  @PluginMethod()
  public void listFiles(PluginCall call) {

    // get input arguments
    String uriString = call.getString("uri", "");
    if (uriString == "") {
      call.reject("Invalid uri", ERR_INVALID_URI);
      return;
    }

    // Get a DocumentFile from the given TreeUri
    var uri = Uri.parse(uriString);
    var folder = DocumentFile.fromTreeUri(getContext(), uri);
    if (!folder.exists()) {
      call.reject("Folder not found", ERR_NOT_FOUND);
      return;
    }

    // return files list
    try {
      var ret = new JSObject();
      ret.put("items", this.listFileFaster(folder.getUri()));
      call.resolve(ret);
    }
    catch (Exception e) {
      call.reject("Error retrieving files list", ERR_IO_EXCEPTION, e);
    }

  }

  /**
   * Load and return file content
   *
   * @param call
   *  call.uri: URI of the file to read
   */
  @PluginMethod()
  public void readFile(PluginCall call) {

    // get input arguments
    String fileUri = call.getString("uri", null);
    if (fileUri == null) {
      call.reject("Invalid or missing uri", ERR_INVALID_URI);
      return;
    }

    // Get a DocumentFile from the given TreeUri
    var uri = Uri.parse(fileUri);
    var file = DocumentFile.fromSingleUri(getContext(), uri);
    if (!file.exists()) {
      call.reject("File not found", ERR_NOT_FOUND);
      return;
    }

    // load file content
    var stringBuilder = new StringBuilder();
    try (InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
         var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream)))) {
      String line;
      while ((line = reader.readLine()) != null) {
        stringBuilder.append(line);
      }
    }
    catch (FileNotFoundException e) {
      call.reject(e.toString(), ERR_NOT_FOUND);
      return;
    } catch (IOException e) {
      call.reject(e.toString(), ERR_IO_EXCEPTION);
      return;
    }

    // return file content
    var ret = new JSObject();
    ret.put("content", stringBuilder.toString());
    call.resolve(ret);

  }

  /**
   * Delete file
   *
   * @param call
   *  call.uri: URI of the file to delete
   */
  @PluginMethod()
  public void deleteFile(PluginCall call) {

    // get input arguments
    String fileUri = call.getString("uri", null);
    if (fileUri == null) {
      call.reject("Invalid or missing uri", ERR_INVALID_URI);
      return;
    }

    // Get a DocumentFile from the given TreeUri
    var uri = Uri.parse(fileUri);
    var file = DocumentFile.fromSingleUri(getContext(), uri);
    if (!file.exists()) {
      call.reject("File not found", ERR_NOT_FOUND);
      return;
    }

    // delete file
    var res = file.delete();
    if (res) {
      call.resolve();
    }
    else {
      call.reject("Error deleting file", ERR_IO_EXCEPTION);
    }

  }

  /**
   * More efficient method to retrieve directory content, avoiding calls to
   * slow DocumentFile methods like .getDisplayName()
   *
   * @see https://stackoverflow.com/questions/42186820/why-is-documentfile-so-slow-and-what-should-i-use-instead
   *
   * @param folderUri URI of the folder to be scanned
   *
   * @return JSArray of JSObject items, ready to be returned to JS
   */
  private JSArray listFileFaster(Uri folderUri) {

    final ContentResolver resolver = getContext().getContentResolver();
    final Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getDocumentId(folderUri));
    final var result = new JSArray();
    Cursor c;

    // load all of the needed data in a single shot
    c = resolver.query(
      childrenUri,
      new String[] {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,    // 0
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,   // 1
        DocumentsContract.Document.COLUMN_MIME_TYPE,      // 2
        DocumentsContract.Document.COLUMN_FLAGS,          // 3
        DocumentsContract.Document.COLUMN_SIZE,           // 4
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,  // 5
      }, null, null, null);
    while (c.moveToNext()) {
      final var item = new JSObject();
      final var documentId = c.getString(0);
      final var mimeType = c.getString(2);
      final var flags = c.getInt(3);

      item.put("name", c.getString(1));
      item.put("uri", DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId).toString());
      item.put("type", mimeType);
      item.put("isDirectory", mimeType == DocumentsContract.Document.MIME_TYPE_DIR);
      item.put("isVirtual", flags & DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT);
      item.put("size", c.getLong(4));
      item.put("lastModified", c.getLong(5));

      // append to result
      result.put(item);
    }

    return result;
  }

}
