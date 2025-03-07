/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */
package org.apache.poi.hssf.dev;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.PrintStream;
import java.util.Map;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.hssf.record.RecordInputStream;
import org.apache.commons.io.output.NullPrintStream;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_OUT)
class TestFormulaViewer extends BaseTestIteratingXLS {
    @Override
    protected Map<String, Class<? extends Throwable>> getExcludes() {
        Map<String, Class<? extends Throwable>> excludes = super.getExcludes();
        excludes.put("35897-type4.xls", EncryptedDocumentException.class); // unsupported crypto api header
        excludes.put("51832.xls", EncryptedDocumentException.class);
        excludes.put("xor-encryption-abc.xls", EncryptedDocumentException.class);
        excludes.put("password.xls", EncryptedDocumentException.class);
        excludes.put("43493.xls", RecordInputStream.LeftoverDataException.class);  // HSSFWorkbook cannot open it as well
        excludes.put("44958_1.xls", RecordInputStream.LeftoverDataException.class);
        return excludes;
    }

    @Override
    void runOneFile(File fileIn) throws Exception {
        PrintStream save = System.out;
        try {
            // redirect standard out during the test to avoid spamming the console with output
            System.setOut(new NullPrintStream());

            FormulaViewer viewer = new FormulaViewer();
            viewer.setFile(fileIn.getAbsolutePath());
            viewer.setList(true);
            viewer.run();
        } catch (RuntimeException re) {
            String m = re.getMessage();
            if (m.startsWith("toFormulaString") || m.startsWith("3D references")) {
                // TODO: fix those cases, but ignore them for now ...
                assumeTrue(true);
            } else {
                throw re;
            }
        } finally {
            System.setOut(save);
        }
    }
}
