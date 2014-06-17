package org.kevoree.npm.resolver;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;

/**
 * NpmResolver
 *
 */
public class NpmResolver {

    /**
     *
     * @param pkgName
     * @param pkgVersion
     * @return File (package .tar file)
     * @throws Exception
     */
    public File resolve(String pkgName, String pkgVersion) throws Exception {
        if (pkgName != null && pkgName.length() > 0) {
            if (pkgVersion == null || (pkgVersion != null && pkgVersion.length() == 0)) {
                pkgVersion = "latest";
            }

            URL url = new URL("https://registry.npmjs.org/"+pkgName+"/"+pkgVersion);
            URLConnection conn = url.openConnection();
            InputStream is = conn.getInputStream();
            JsonParser parser = new JsonParser();
            JsonObject response = (JsonObject) parser.parse(new InputStreamReader(is));
            is.close();
            pkgVersion = response.get("version").getAsString();

            if (response.has("dist") && response.getAsJsonObject("dist").has("tarball")) {
                URL tarUrl = new URL(response.getAsJsonObject("dist").get("tarball").getAsString());
                InputStream in = tarUrl.openStream();
                File tgzFile = File.createTempFile(pkgName + "-" + pkgVersion + "_", ".tgz");
                FileOutputStream fos = new FileOutputStream(tgzFile);
                byte data[] = new byte[1024];
                int count;
                while ((count = in.read(data, 0, 1024)) != -1) {
                    fos.write(data, 0, count);
                }
                in.close();
                fos.close();

                FileInputStream fin = new FileInputStream(tgzFile.getAbsolutePath());
                BufferedInputStream bis = new BufferedInputStream(fin);
                File tarFile = File.createTempFile(pkgName + "-" + pkgVersion + "_", ".tar");
                FileOutputStream out = new FileOutputStream(tarFile.getAbsolutePath());
                GzipCompressorInputStream gzIn = new GzipCompressorInputStream(bis);
                final byte[] buffer = new byte[1024];
                int n;
                while (-1 != (n = gzIn.read(buffer))) {
                    out.write(buffer, 0, n);
                }
                out.close();
                gzIn.close();
                tgzFile.delete();

                return tarFile;

            } else {
                throw new Exception("Module not found at "+url);
            }
        } else {
            throw new Exception("You must give a package name to resolve");
        }
    }

    public static void main(String[] args) throws Exception {
        // TODO: refactor as a proper test
        NpmResolver resolver = new NpmResolver();
        File resolved = resolver.resolve("kevoree-chan-websocket", "latest");
        if (resolved != null && resolved.exists()) {
            TarArchiveInputStream tarInput = new TarArchiveInputStream(new FileInputStream(resolved));
            try {
                TarArchiveEntry entry;
                while (null!=(entry=tarInput.getNextTarEntry())) {
                    if (entry.getName().equals("package/kevlib.json")) {
                        int entrySize = (int) entry.getSize();
                        byte[] content = new byte[entrySize];
                        tarInput.read(content, 0, entrySize);
                        File output = File.createTempFile("kevoree-chan-websocket_latest", ".json");
                        FileOutputStream fos = new FileOutputStream(output);
                        fos.write(content);
                        fos.close();
                    }
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            } finally {
                tarInput.close();
            }
        } else {
            System.out.println("NpmResolver error: Can't resolve TypeDefinition kevoree-chan-websocket:latest");
        }
    }
}
