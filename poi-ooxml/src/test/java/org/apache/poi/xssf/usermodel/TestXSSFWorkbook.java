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

package org.apache.poi.xssf.usermodel;

import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;
import static org.apache.poi.hssf.HSSFTestDataSamples.openSampleFileStream;
import static org.apache.poi.xssf.XSSFTestDataSamples.openSampleWorkbook;
import static org.apache.poi.xssf.XSSFTestDataSamples.writeOut;
import static org.apache.poi.xssf.XSSFTestDataSamples.writeOutAndReadBack;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.zip.CRC32;

import org.apache.poi.POIDataSamples;
import org.apache.poi.hssf.HSSFTestDataSamples;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.ContentTypes;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.internal.FileHelper;
import org.apache.poi.openxml4j.opc.internal.MemoryPackagePart;
import org.apache.poi.openxml4j.opc.internal.PackagePropertiesPart;
import org.apache.poi.ss.tests.usermodel.BaseTestXWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.LocaleUtil;
import org.apache.poi.util.TempFile;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xssf.XSSFITestDataProvider;
import org.apache.poi.xssf.model.StylesTable;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCalcPr;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTPivotCache;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTWorkbookPr;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCalcMode;

public final class TestXSSFWorkbook extends BaseTestXWorkbook {

    public TestXSSFWorkbook() {
        super(XSSFITestDataProvider.instance);
    }

    /**
     * Tests that we can save, and then re-load a new document
     */
    @Test
    void saveLoadNew() throws IOException, InvalidFormatException {
        File file;
        try (XSSFWorkbook wb1 = new XSSFWorkbook()) {

            //check that the default date system is set to 1900
            CTWorkbookPr pr = wb1.getCTWorkbook().getWorkbookPr();
            assertNotNull(pr);
            assertTrue(pr.isSetDate1904());
            assertFalse(pr.getDate1904(), "XSSF must use the 1900 date system");

            Sheet sheet1 = wb1.createSheet("sheet1");
            Sheet sheet2 = wb1.createSheet("sheet2");
            wb1.createSheet("sheet3");

            RichTextString rts = wb1.getCreationHelper().createRichTextString("hello world");

            sheet1.createRow(0).createCell((short) 0).setCellValue(1.2);
            sheet1.createRow(1).createCell((short) 0).setCellValue(rts);
            sheet2.createRow(0);

            assertEquals(0, wb1.getSheetAt(0).getFirstRowNum());
            assertEquals(1, wb1.getSheetAt(0).getLastRowNum());
            assertEquals(0, wb1.getSheetAt(1).getFirstRowNum());
            assertEquals(0, wb1.getSheetAt(1).getLastRowNum());
            assertEquals(-1, wb1.getSheetAt(2).getFirstRowNum());
            assertEquals(-1, wb1.getSheetAt(2).getLastRowNum());

            file = writeOut(wb1, "poi-.xlsx");
        }

        // Check the package contains what we'd expect it to
        try (OPCPackage pkg = OPCPackage.open(file.toString())) {
            PackagePart wbRelPart =
                pkg.getPart(PackagingURIHelper.createPartName("/xl/_rels/workbook.xml.rels"));
            assertNotNull(wbRelPart);
            assertTrue(wbRelPart.isRelationshipPart());
            assertEquals(ContentTypes.RELATIONSHIPS_PART, wbRelPart.getContentType());

            PackagePart wbPart =
                pkg.getPart(PackagingURIHelper.createPartName("/xl/workbook.xml"));
            // Links to the three sheets, shared strings and styles
            assertTrue(wbPart.hasRelationships());
            assertEquals(5, wbPart.getRelationships().size());

            // Load back the XSSFWorkbook
            try (XSSFWorkbook wb2 = new XSSFWorkbook(pkg)) {
                assertEquals(3, wb2.getNumberOfSheets());
                assertNotNull(wb2.getSheetAt(0));
                assertNotNull(wb2.getSheetAt(1));
                assertNotNull(wb2.getSheetAt(2));

                assertNotNull(wb2.getSharedStringSource());
                assertNotNull(wb2.getStylesSource());

                assertEquals(0, wb2.getSheetAt(0).getFirstRowNum());
                assertEquals(1, wb2.getSheetAt(0).getLastRowNum());
                assertEquals(0, wb2.getSheetAt(1).getFirstRowNum());
                assertEquals(0, wb2.getSheetAt(1).getLastRowNum());
                assertEquals(-1, wb2.getSheetAt(2).getFirstRowNum());
                assertEquals(-1, wb2.getSheetAt(2).getLastRowNum());

                Sheet sheet1 = wb2.getSheetAt(0);
                assertEquals(1.2, sheet1.getRow(0).getCell(0).getNumericCellValue(), 0.0001);
                assertEquals("hello world", sheet1.getRow(1).getCell(0).getRichStringCellValue().getString());
            }
        }
    }

    @Test
    void existing() throws Exception {
        try (XSSFWorkbook workbook = openSampleWorkbook("Formatting.xlsx");
             OPCPackage pkg = OPCPackage.open(openSampleFileStream("Formatting.xlsx"))) {
            assertNotNull(workbook.getSharedStringSource());
            assertNotNull(workbook.getStylesSource());

            // And check a few low level bits too
            PackagePart wbPart = pkg.getPart(PackagingURIHelper.createPartName("/xl/workbook.xml"));

            // Links to the three sheets, shared, styles and themes
            assertTrue(wbPart.hasRelationships());
            assertEquals(6, wbPart.getRelationships().size());
        }
    }

    @Test
    void getCellStyleAt() throws IOException{
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            short i = 0;
            //get default style
            CellStyle cellStyleAt = workbook.getCellStyleAt(i);
            assertNotNull(cellStyleAt);

            //get custom style
            StylesTable styleSource = workbook.getStylesSource();
            XSSFCellStyle customStyle = new XSSFCellStyle(styleSource);
            XSSFFont font = new XSSFFont();
            font.setFontName("Verdana");
            customStyle.setFont(font);
            int x = styleSource.putStyle(customStyle);
            cellStyleAt = workbook.getCellStyleAt((short) x);
            assertNotNull(cellStyleAt);
        }
    }

    @Test
    void getFontAt() throws IOException{
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            StylesTable styleSource = workbook.getStylesSource();
            short i = 0;
            //get default font
            Font fontAt = workbook.getFontAt(i);
            assertNotNull(fontAt);

            //get customized font
            XSSFFont customFont = new XSSFFont();
            customFont.setItalic(true);
            int x = styleSource.putFont(customFont);
            fontAt = workbook.getFontAt((short) x);
            assertNotNull(fontAt);
        }
    }

    @Test
    void getNumCellStyles() throws IOException{
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            //get default cellStyles
            assertEquals(1, workbook.getNumCellStyles());
        }
    }

    @Test
    void loadSave() throws IOException {
        XSSFWorkbook workbook = openSampleWorkbook("Formatting.xlsx");
        assertEquals(3, workbook.getNumberOfSheets());
        assertEquals("dd/mm/yyyy", workbook.getSheetAt(0).getRow(1).getCell(0).getRichStringCellValue().getString());
        assertNotNull(workbook.getSharedStringSource());
        assertNotNull(workbook.getStylesSource());

        // Write out, and check
        // Load up again, check all still there
        XSSFWorkbook wb2 = writeOutAndReadBack(workbook);
        assertEquals(3, wb2.getNumberOfSheets());
        assertNotNull(wb2.getSheetAt(0));
        assertNotNull(wb2.getSheetAt(1));
        assertNotNull(wb2.getSheetAt(2));

        assertEquals("dd/mm/yyyy", wb2.getSheetAt(0).getRow(1).getCell(0).getRichStringCellValue().getString());
        assertEquals("yyyy/mm/dd", wb2.getSheetAt(0).getRow(2).getCell(0).getRichStringCellValue().getString());
        assertEquals("yyyy-mm-dd", wb2.getSheetAt(0).getRow(3).getCell(0).getRichStringCellValue().getString());
        assertEquals("yy/mm/dd", wb2.getSheetAt(0).getRow(4).getCell(0).getRichStringCellValue().getString());
        assertNotNull(wb2.getSharedStringSource());
        assertNotNull(wb2.getStylesSource());

        workbook.close();
        wb2.close();
    }

    @Test
    void styles() throws IOException {
        try (XSSFWorkbook wb1 = openSampleWorkbook("Formatting.xlsx")) {
            StylesTable ss = wb1.getStylesSource();
            assertNotNull(ss);
            StylesTable st = ss;

            // Has 8 number formats
            assertEquals(8, st.getNumDataFormats());
            // Has 2 fonts
            assertEquals(2, st.getFonts().size());
            // Has 2 fills
            assertEquals(2, st.getFills().size());
            // Has 1 border
            assertEquals(1, st.getBorders().size());

            // Add two more styles
            assertEquals(StylesTable.FIRST_CUSTOM_STYLE_ID + 8,
                st.putNumberFormat("testFORMAT"));
            assertEquals(StylesTable.FIRST_CUSTOM_STYLE_ID + 8,
                st.putNumberFormat("testFORMAT"));
            assertEquals(StylesTable.FIRST_CUSTOM_STYLE_ID + 9,
                st.putNumberFormat("testFORMAT2"));
            assertEquals(10, st.getNumDataFormats());


            // Save, load back in again, and check
            try (XSSFWorkbook wb2 = writeOutAndReadBack(wb1)) {
                ss = wb2.getStylesSource();
                assertNotNull(ss);

                assertEquals(10, st.getNumDataFormats());
                assertEquals(2, st.getFonts().size());
                assertEquals(2, st.getFills().size());
                assertEquals(1, st.getBorders().size());
            }
        }
    }

    @Test
    void incrementSheetId() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            int sheetId = (int) wb.createSheet().sheet.getSheetId();
            assertEquals(1, sheetId);
            sheetId = (int) wb.createSheet().sheet.getSheetId();
            assertEquals(2, sheetId);

            //test file with gaps in the sheetId sequence
            try (XSSFWorkbook wbBack = openSampleWorkbook("47089.xlsm")) {
                int lastSheetId = (int) wbBack.getSheetAt(wbBack.getNumberOfSheets() - 1).sheet.getSheetId();
                sheetId = (int) wbBack.createSheet().sheet.getSheetId();
                assertEquals(lastSheetId + 1, sheetId);
            }
        }
    }

    /**
     *  Test setting of core properties such as Title and Author
     */
    @Test
    void workbookProperties() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            POIXMLProperties props = workbook.getProperties();
            assertNotNull(props);
            //the Application property must be set for new workbooks, see Bugzilla #47559
            assertEquals("Apache POI", props.getExtendedProperties().getUnderlyingProperties().getApplication());

            PackagePropertiesPart opcProps = props.getCoreProperties().getUnderlyingProperties();
            assertNotNull(opcProps);

            opcProps.setTitleProperty("Testing Bugzilla #47460");
            assertEquals("Apache POI", opcProps.getCreatorProperty().orElse(""));
            opcProps.setCreatorProperty("poi-dev@poi.apache.org");

            try (XSSFWorkbook wbBack = writeOutAndReadBack(workbook)) {
                assertEquals("Apache POI", wbBack.getProperties().getExtendedProperties().getUnderlyingProperties().getApplication());
                opcProps = wbBack.getProperties().getCoreProperties().getUnderlyingProperties();
                assertEquals("Testing Bugzilla #47460", opcProps.getTitleProperty().orElse(""));
                assertEquals("poi-dev@poi.apache.org", opcProps.getCreatorProperty().orElse(""));
            }
        }
    }

    /**
     * Verify that the attached test data was not modified. If this test method
     * fails, the test data is not working properly.
     */
    @Test
    void bug47668() throws Exception {
        try (XSSFWorkbook workbook = openSampleWorkbook("47668.xlsx")) {
            List<XSSFPictureData> allPictures = workbook.getAllPictures();
            assertEquals(1, allPictures.size());

            PackagePartName imagePartName = PackagingURIHelper
                .createPartName("/xl/media/image1.jpeg");
            PackagePart imagePart = workbook.getPackage().getPart(imagePartName);
            assertNotNull(imagePart);

            for (XSSFPictureData pictureData : allPictures) {
                PackagePart picturePart = pictureData.getPackagePart();
                assertSame(imagePart, picturePart);
            }

            XSSFSheet sheet0 = workbook.getSheetAt(0);
            XSSFDrawing drawing0 = sheet0.createDrawingPatriarch();
            XSSFPictureData pictureData0 = (XSSFPictureData) drawing0.getRelations().get(0);
            byte[] data0 = pictureData0.getData();
            CRC32 crc0 = new CRC32();
            crc0.update(data0);

            XSSFSheet sheet1 = workbook.getSheetAt(1);
            XSSFDrawing drawing1 = sheet1.createDrawingPatriarch();
            XSSFPictureData pictureData1 = (XSSFPictureData) drawing1.getRelations().get(0);
            byte[] data1 = pictureData1.getData();
            CRC32 crc1 = new CRC32();
            crc1.update(data1);

            assertEquals(crc0.getValue(), crc1.getValue());
        }
    }

    /**
     * When deleting a sheet make sure that we adjust sheet indices of named ranges
     */
    @SuppressWarnings("deprecation")
    @Test
    void bug47737() throws IOException {
        try (XSSFWorkbook wb = openSampleWorkbook("47737.xlsx")) {
            assertEquals(2, wb.getNumberOfNames());
            assertNotNull(wb.getCalculationChain());

            XSSFName nm0 = wb.getNameAt(0);
            assertTrue(nm0.getCTName().isSetLocalSheetId());
            assertEquals(0, nm0.getCTName().getLocalSheetId());

            XSSFName nm1 = wb.getNameAt(1);
            assertTrue(nm1.getCTName().isSetLocalSheetId());
            assertEquals(1, nm1.getCTName().getLocalSheetId());

            wb.removeSheetAt(0);
            assertEquals(1, wb.getNumberOfNames());
            XSSFName nm2 = wb.getNameAt(0);
            assertTrue(nm2.getCTName().isSetLocalSheetId());
            assertEquals(0, nm2.getCTName().getLocalSheetId());
            //calculation chain is removed as well
            assertNull(wb.getCalculationChain());
        }
    }

    /**
     * Problems with XSSFWorkbook.removeSheetAt when workbook contains charts
     */
    @Test
    void bug47813() throws IOException {
        try (XSSFWorkbook wb1 = openSampleWorkbook("47813.xlsx")) {
            assertEquals(3, wb1.getNumberOfSheets());
            assertNotNull(wb1.getCalculationChain());

            assertEquals("Numbers", wb1.getSheetName(0));
            //the second sheet is of type 'chartsheet'
            assertEquals("Chart", wb1.getSheetName(1));
            assertTrue(wb1.getSheetAt(1) instanceof XSSFChartSheet);
            assertEquals("SomeJunk", wb1.getSheetName(2));

            wb1.removeSheetAt(2);
            assertEquals(2, wb1.getNumberOfSheets());
            assertNull(wb1.getCalculationChain());

            try (XSSFWorkbook wb2 = writeOutAndReadBack(wb1)) {
                assertEquals(2, wb2.getNumberOfSheets());
                assertNull(wb2.getCalculationChain());

                assertEquals("Numbers", wb2.getSheetName(0));
                assertEquals("Chart", wb2.getSheetName(1));
            }
        }
    }

    /**
     * Problems with the count of the number of styles
     *  coming out wrong
     */
    @Test
    void bug49702() throws IOException {
        // First try with a new file
        try (XSSFWorkbook wb1 = new XSSFWorkbook()) {

            // Should have one style
            assertEquals(1, wb1.getNumCellStyles());
            wb1.getCellStyleAt((short) 0);
            assertNull(wb1.getCellStyleAt((short) 1), "Shouldn't be able to get style at 0 that doesn't exist");

            // Add another one
            CellStyle cs = wb1.createCellStyle();
            cs.setDataFormat((short) 11);

            // Re-check
            assertEquals(2, wb1.getNumCellStyles());
            wb1.getCellStyleAt((short) 0);
            wb1.getCellStyleAt((short) 1);
            assertNull(wb1.getCellStyleAt((short) 2), "Shouldn't be able to get style at 2 that doesn't exist");

            // Save and reload
            try (XSSFWorkbook nwb = writeOutAndReadBack(wb1)) {
                assertEquals(2, nwb.getNumCellStyles());
                nwb.getCellStyleAt((short) 0);
                nwb.getCellStyleAt((short) 1);
                assertNull(nwb.getCellStyleAt((short) 2), "Shouldn't be able to get style at 2 that doesn't exist");

                // Now with an existing file
                try (XSSFWorkbook wb2 = openSampleWorkbook("sample.xlsx")) {
                    assertEquals(3, wb2.getNumCellStyles());
                    wb2.getCellStyleAt((short) 0);
                    wb2.getCellStyleAt((short) 1);
                    wb2.getCellStyleAt((short) 2);
                    assertNull(wb2.getCellStyleAt((short) 3), "Shouldn't be able to get style at 3 that doesn't exist");
                }
            }
        }
    }

    @Test
    void recalcId() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            assertFalse(wb.getForceFormulaRecalculation());
            CTWorkbook ctWorkbook = wb.getCTWorkbook();
            assertFalse(ctWorkbook.isSetCalcPr());

            wb.setForceFormulaRecalculation(true);

            CTCalcPr calcPr = ctWorkbook.getCalcPr();
            assertNotNull(calcPr);
            assertEquals(0, (int) calcPr.getCalcId());

            calcPr.setCalcId(100);
            assertTrue(wb.getForceFormulaRecalculation());

            wb.setForceFormulaRecalculation(false);
            assertFalse(wb.getForceFormulaRecalculation());

            // calcMode="manual" is unset when forceFormulaRecalculation=true
            calcPr.setCalcMode(STCalcMode.MANUAL);
            wb.setForceFormulaRecalculation(true);
            assertSame(STCalcMode.AUTO, calcPr.getCalcMode());
            assertTrue(wb.getForceFormulaRecalculation());

            wb.setForceFormulaRecalculation(false);
            assertFalse(wb.getForceFormulaRecalculation());

            wb.setForceFormulaRecalculation(true);
            assertTrue(wb.getForceFormulaRecalculation());
        }
    }

    @Test
    void columnWidthPOI52233() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            XSSFRow row = sheet.createRow(0);
            XSSFCell cell = row.createCell(0);
            cell.setCellValue("hello world");

            sheet = workbook.createSheet();
            sheet.setColumnWidth(4, 5000);
            sheet.setColumnWidth(5, 5000);

            sheet.groupColumn((short) 4, (short) 5);

            accessWorkbook(workbook);
            workbook.write(NULL_OUTPUT_STREAM);
            accessWorkbook(workbook);
        }
    }

    private void accessWorkbook(XSSFWorkbook workbook) {
        workbook.getSheetAt(1).setColumnGroupCollapsed(4, true);
        workbook.getSheetAt(1).setColumnGroupCollapsed(4, false);

        assertEquals("hello world", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        assertEquals(2048, workbook.getSheetAt(0).getColumnWidth(0)); // <-works
    }

    @Test
    void bug48495() throws IOException {
        try (Workbook wb = openSampleWorkbook("48495.xlsx")) {
            assertSheetOrder(wb, "Sheet1");

            Sheet sheet = wb.getSheetAt(0);
            sheet.shiftRows(2, sheet.getLastRowNum(), 1, true, false);
            Row newRow = sheet.getRow(2);
            if (newRow == null) {
                newRow = sheet.createRow(2);
            }
            newRow.createCell(0).setCellValue(" Another Header");
            wb.cloneSheet(0);

            assertSheetOrder(wb, "Sheet1", "Sheet1 (2)");

            try (Workbook read = writeOutAndReadBack(wb)) {
                assertNotNull(read);
                assertSheetOrder(read, "Sheet1", "Sheet1 (2)");
            }
        }
    }

    @Test
    void bug47090a() throws IOException {
        try (Workbook workbook = openSampleWorkbook("47090.xlsx")) {
            assertSheetOrder(workbook, "Sheet1", "Sheet2");
            workbook.removeSheetAt(0);
            assertSheetOrder(workbook, "Sheet2");
            workbook.createSheet();
            assertSheetOrder(workbook, "Sheet2", "Sheet1");
            try (Workbook read = writeOutAndReadBack(workbook)) {
                assertSheetOrder(read, "Sheet2", "Sheet1");
            }
        }
    }

    @Test
    void bug47090b() throws IOException {
        try (Workbook workbook = openSampleWorkbook("47090.xlsx")) {
            assertSheetOrder(workbook, "Sheet1", "Sheet2");
            workbook.removeSheetAt(1);
            assertSheetOrder(workbook, "Sheet1");
            workbook.createSheet();
            assertSheetOrder(workbook, "Sheet1", "Sheet0");        // Sheet0 because it uses "Sheet" + sheets.size() as starting point!
            try (Workbook read = writeOutAndReadBack(workbook)) {
                assertSheetOrder(read, "Sheet1", "Sheet0");
            }
        }
    }

    @Test
    void bug47090c() throws IOException {
        try (Workbook workbook = openSampleWorkbook("47090.xlsx")) {
            assertSheetOrder(workbook, "Sheet1", "Sheet2");
            workbook.removeSheetAt(0);
            assertSheetOrder(workbook, "Sheet2");
            workbook.cloneSheet(0);
            assertSheetOrder(workbook, "Sheet2", "Sheet2 (2)");
            try (Workbook read = writeOutAndReadBack(workbook)) {
                assertSheetOrder(read, "Sheet2", "Sheet2 (2)");
            }
        }
    }

    @Test
    void bug47090d() throws IOException {
        try (Workbook workbook = openSampleWorkbook("47090.xlsx")) {
            assertSheetOrder(workbook, "Sheet1", "Sheet2");
            workbook.createSheet();
            assertSheetOrder(workbook, "Sheet1", "Sheet2", "Sheet0");
            workbook.removeSheetAt(0);
            assertSheetOrder(workbook, "Sheet2", "Sheet0");
            workbook.createSheet();
            assertSheetOrder(workbook, "Sheet2", "Sheet0", "Sheet1");
            try (Workbook read = writeOutAndReadBack(workbook)) {
                assertSheetOrder(read, "Sheet2", "Sheet0", "Sheet1");
            }
        }
    }

    @Test
    void bug51158() throws IOException {
        // create a workbook
        try (XSSFWorkbook wb1 = new XSSFWorkbook()) {
            XSSFSheet sheet = wb1.createSheet("Test Sheet");
            XSSFRow row = sheet.createRow(2);
            XSSFCell cell = row.createCell(3);
            cell.setCellValue("test1");

            //XSSFCreationHelper helper = workbook.getCreationHelper();
            //cell.setHyperlink(helper.createHyperlink(0));

            XSSFComment comment = sheet.createDrawingPatriarch().createCellComment(new XSSFClientAnchor());
            assertNotNull(comment);
            comment.setString("some comment");

//        CellStyle cs = workbook.createCellStyle();
//        cs.setShrinkToFit(false);
//        row.createCell(0).setCellStyle(cs);

            // write the first excel file
            try (XSSFWorkbook wb2 = writeOutAndReadBack(wb1)) {
                assertNotNull(wb2);
                sheet = wb2.getSheetAt(0);
                row = sheet.getRow(2);
                assertEquals("test1", row.getCell(3).getStringCellValue());
                assertNull(row.getCell(4));

                // add a new cell to the sheet
                cell = row.createCell(4);
                cell.setCellValue("test2");

                // write the second excel file
                try (XSSFWorkbook wb3 = writeOutAndReadBack(wb2)) {
                    assertNotNull(wb3);
                    sheet = wb3.getSheetAt(0);
                    row = sheet.getRow(2);

                    assertEquals("test1", row.getCell(3).getStringCellValue());
                    assertEquals("test2", row.getCell(4).getStringCellValue());
                }
            }
        }
    }

    @Test
    void bug51158a() throws IOException {
        // create a workbook
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Test Sheet");

            XSSFSheet sheetBack = workbook.getSheetAt(0);

            // committing twice did add the XML twice without clearing the part in between
            sheetBack.commit();

            // ensure that a memory based package part does not have lingering data from previous commit() calls
            if (sheetBack.getPackagePart() instanceof MemoryPackagePart) {
                sheetBack.getPackagePart().clear();
            }

            sheetBack.commit();

            String str = new String(IOUtils.toByteArray(sheetBack.getPackagePart().getInputStream()), StandardCharsets.UTF_8);

            assertEquals(1, countMatches(str, "<worksheet"));
        }
    }

    @Test
    void bug60509() throws Exception {
        try (XSSFWorkbook wb = openSampleWorkbook("60509.xlsx")) {
            assertSheetOrder(wb, "Sheet1", "Sheet2", "Sheet3");
            int sheetIndex = wb.getSheetIndex("Sheet1");
            wb.setSheetName(sheetIndex, "Sheet1-Renamed");
            try (Workbook read = writeOutAndReadBack(wb)) {
                assertNotNull(read);
                assertSheetOrder(read, "Sheet1-Renamed", "Sheet2", "Sheet3");
                XSSFSheet sheet = (XSSFSheet) read.getSheet("Sheet1-Renamed");
                XDDFChartData.Series series = sheet.getDrawingPatriarch().getCharts().get(0).getChartSeries().get(0).getSeries(0);
                assertTrue(series instanceof XDDFBarChartData.Series, "should be a bar chart data series");
                String formula = series.getCategoryData().getFormula();
                assertTrue(formula.startsWith("'Sheet1-Renamed'!"), "should contain new sheet name");
            }
        }
    }

    private static final int INDEX_NOT_FOUND = -1;

    private static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    private static int countMatches(CharSequence str, CharSequence sub) {
        if (isEmpty(str) || isEmpty(sub)) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = indexOf(str, sub, idx)) != INDEX_NOT_FOUND) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private static int indexOf(CharSequence cs, CharSequence searchChar, int start) {
        return cs.toString().indexOf(searchChar.toString(), start);
    }

    @Test
    void testAddPivotCache() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CTWorkbook ctWb = wb.getCTWorkbook();
            CTPivotCache pivotCache = wb.addPivotCache("0");
            //Ensures that pivotCaches is initiated
            assertTrue(ctWb.isSetPivotCaches());
            assertSame(pivotCache, ctWb.getPivotCaches().getPivotCacheArray(0));
            assertEquals("0", pivotCache.getId());
        }
    }

    private void setPivotData(XSSFWorkbook wb){
        XSSFSheet sheet = wb.createSheet();

        Row row1 = sheet.createRow(0);
        // Create a cell and put a value in it.
        Cell cell = row1.createCell(0);
        cell.setCellValue("Names");
        Cell cell2 = row1.createCell(1);
        cell2.setCellValue("#");
        Cell cell7 = row1.createCell(2);
        cell7.setCellValue("Data");

        Row row2 = sheet.createRow(1);
        Cell cell3 = row2.createCell(0);
        cell3.setCellValue("Jan");
        Cell cell4 = row2.createCell(1);
        cell4.setCellValue(10);
        Cell cell8 = row2.createCell(2);
        cell8.setCellValue("Apa");

        Row row3 = sheet.createRow(2);
        Cell cell5 = row3.createCell(0);
        cell5.setCellValue("Ben");
        Cell cell6 = row3.createCell(1);
        cell6.setCellValue(9);
        Cell cell9 = row3.createCell(2);
        cell9.setCellValue("Bepa");

        AreaReference source = wb.getCreationHelper().createAreaReference("A1:B2");
        sheet.createPivotTable(source, new CellReference("H5"));
    }

    @Test
    void testLoadWorkbookWithPivotTable() throws Exception {
        File file = TempFile.createTempFile("ooxml-pivottable", ".xlsx");

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            setPivotData(wb);

            FileOutputStream fileOut = new FileOutputStream(file);
            wb.write(fileOut);
            fileOut.close();
        }

        try (XSSFWorkbook wb2 = (XSSFWorkbook) WorkbookFactory.create(file)) {
            assertEquals(1, wb2.getPivotTables().size());
        }

        assertTrue(file.delete());
    }

    @Test
    void testAddPivotTableToWorkbookWithLoadedPivotTable() throws Exception {
        File file = TempFile.createTempFile("ooxml-pivottable", ".xlsx");

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            setPivotData(wb);

            try (FileOutputStream fileOut = new FileOutputStream(file)) {
                wb.write(fileOut);
            }
        }

        try (XSSFWorkbook wb2 = (XSSFWorkbook) WorkbookFactory.create(file)) {
            setPivotData(wb2);
            assertEquals(2, wb2.getPivotTables().size());
        }

        assertTrue(file.delete());
    }

    @Test
    void testSetFirstVisibleTab_57373() throws IOException {

        try (Workbook wb = new XSSFWorkbook()) {
            /*Sheet sheet1 =*/
            wb.createSheet();
            Sheet sheet2 = wb.createSheet();
            int idx2 = wb.getSheetIndex(sheet2);
            Sheet sheet3 = wb.createSheet();
            int idx3 = wb.getSheetIndex(sheet3);

            // add many sheets so "first visible" is relevant
            for (int i = 0; i < 30; i++) {
                wb.createSheet();
            }

            wb.setFirstVisibleTab(idx2);
            wb.setActiveSheet(idx3);

            //wb.write(new FileOutputStream(new File("C:\\temp\\test.xlsx")));

            assertEquals(idx2, wb.getFirstVisibleTab());
            assertEquals(idx3, wb.getActiveSheetIndex());

            try (Workbook wbBack = writeOutAndReadBack(wb)) {
                sheet2 = wbBack.getSheetAt(idx2);
                assertNotNull(sheet2);
                sheet3 = wbBack.getSheetAt(idx3);
                assertNotNull(sheet3);
                assertEquals(idx2, wb.getFirstVisibleTab());
                assertEquals(idx3, wb.getActiveSheetIndex());
            }
        }
    }

    /**
     * Tests that we can save a workbook with macros and reload it.
     */
    @Test
    void testSetVBAProject() throws Exception {
        File file;
        final byte[] allBytes = new byte[256];
        for (int i = 0; i < 256; i++) {
            allBytes[i] = (byte) (i - 128);
        }

        try (XSSFWorkbook wb1 = new XSSFWorkbook()) {
            wb1.createSheet();
            wb1.setVBAProject(new ByteArrayInputStream(allBytes));
            file = writeOut(wb1, "ooi-.xlsm");
        }

        // Check the package contains what we'd expect it to
        try (OPCPackage pkg = OPCPackage.open(file.toString())) {
            PackagePart wbPart = pkg.getPart(PackagingURIHelper.createPartName("/xl/workbook.xml"));
            assertTrue(wbPart.hasRelationships());
            final PackageRelationshipCollection relationships = wbPart.getRelationships().getRelationships(XSSFRelation.VBA_MACROS.getRelation());
            assertEquals(1, relationships.size());
            PackageRelationship relationship = relationships.getRelationship(0);
            assertNotNull(relationship);
            assertEquals(XSSFRelation.VBA_MACROS.getDefaultFileName(), relationship.getTargetURI().toString());
            PackagePart vbaPart = pkg.getPart(PackagingURIHelper.createPartName(XSSFRelation.VBA_MACROS.getDefaultFileName()));
            assertNotNull(vbaPart);
            assertFalse(vbaPart.isRelationshipPart());
            assertEquals(XSSFRelation.VBA_MACROS.getContentType(), vbaPart.getContentType());
            final byte[] fromFile = IOUtils.toByteArray(vbaPart.getInputStream());
            assertArrayEquals(allBytes, fromFile);

            // Load back the XSSFWorkbook just to check nothing explodes
            try (XSSFWorkbook wb2 = new XSSFWorkbook(pkg)) {
                assertEquals(1, wb2.getNumberOfSheets());
                assertEquals(XSSFWorkbookType.XLSM, wb2.getWorkbookType());
            }
        }
    }

    @Test
    void testBug54399() throws IOException {
        try (XSSFWorkbook workbook = openSampleWorkbook("54399.xlsx")) {

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                String name = "SheetRenamed" + (i + 1);
                workbook.setSheetName(i, name);
                assertEquals(name, workbook.getSheetName(i));
            }

        }
    }

    /**
     *  {@code Iterator<XSSFSheet> XSSFWorkbook.iterator} was committed in r700472 on 2008-09-30
     *  and has been replaced with {@code Iterator<Sheet> XSSFWorkbook.iterator}
     *
     *  In order to make code for looping over sheets in workbooks standard, regardless
     *  of the type of workbook (HSSFWorkbook, XSSFWorkbook, SXSSFWorkbook), the previously
     *  available {@code Iterator<XSSFSheet> iterator} and {@code Iterator<XSSFSheet> sheetIterator}
     *  have been replaced with {@code Iterator<Sheet>} {@link Sheet#iterator} and
     *  {@code Iterator<Sheet>} {@link Workbook#sheetIterator}. This makes iterating over sheets in a workbook
     *  similar to iterating over rows in a sheet and cells in a row.
     *
     *  Note: this breaks backwards compatibility! Existing codebases will need to
     *  upgrade their code with either of the following options presented in this test case.
     *
     */
    @SuppressWarnings("unchecked")
    @Test
    void bug58245_XSSFSheetIterator() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet();

            // =====================================================================
            // Case 1: Existing code uses XSSFSheet for-each loop
            // =====================================================================

            // Option A:
            for (XSSFSheet sh : (Iterable<XSSFSheet>) (Iterable<? extends Sheet>) wb) {
                sh.createRow(0);
            }

            // Option B (preferred for new code):
            for (Sheet sh : wb) {
                sh.createRow(1);
            }

            // =====================================================================
            // Case 2: Existing code creates an iterator variable
            // =====================================================================

            // Option A:
            {
                Iterator<XSSFSheet> it = (Iterator<XSSFSheet>) (Iterator<? extends Sheet>) wb.iterator();
                XSSFSheet sh = it.next();
                sh.createRow(2);
            }

            // Option B (preferred for new code):
            {
                Iterator<Sheet> it = wb.iterator();
                Sheet sh = it.next();
                sh.createRow(3);
            }

            assertEquals(4, wb.getSheetAt(0).getPhysicalNumberOfRows());


        }
    }

    @Test
    void testBug56957CloseWorkbook() throws Exception {
        File file = TempFile.createTempFile("TestBug56957_", ".xlsx");
        final Date dateExp = LocaleUtil.getLocaleCalendar(2014, 10, 9).getTime();

        try {
            // as the file is written to, we make a copy before actually working on it
            FileHelper.copyFile(HSSFTestDataSamples.getSampleFile("56957.xlsx"), file);

            assertTrue(file.exists());

            // read-only mode works!
            try (Workbook workbook = XSSFWorkbookFactory.createWorkbook(OPCPackage.open(file, PackageAccess.READ))) {
                Date dateAct = workbook.getSheetAt(0).getRow(0).getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK).getDateCellValue();
                assertEquals(dateExp, dateAct);
            }

            try (Workbook workbook = XSSFWorkbookFactory.createWorkbook(OPCPackage.open(file, PackageAccess.READ))) {
                Date dateAct = workbook.getSheetAt(0).getRow(0).getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK).getDateCellValue();
                assertEquals(dateExp, dateAct);
            }

            // now check read/write mode
            try (Workbook workbook = XSSFWorkbookFactory.createWorkbook(OPCPackage.open(file, PackageAccess.READ_WRITE))) {
                Date dateAct = workbook.getSheetAt(0).getRow(0).getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK).getDateCellValue();
                assertEquals(dateExp, dateAct);
            }

            try (Workbook workbook = XSSFWorkbookFactory.createWorkbook(OPCPackage.open(file, PackageAccess.READ_WRITE))) {
                Date dateAct = workbook.getSheetAt(0).getRow(0).getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK).getDateCellValue();
                assertEquals(dateExp, dateAct);
            }
        } finally {
            assertTrue(file.exists());
            assertTrue(file.delete());
        }
    }

    @Test
    void closeDoesNotModifyWorkbook() throws IOException {
        final String filename = "SampleSS.xlsx";
        final File file = POIDataSamples.getSpreadSheetInstance().getFile(filename);
        Workbook wb;

        // Some tests commented out because close() modifies the file
        // See bug 58779

        // String
        //wb = new XSSFWorkbook(file.getPath());
        //assertCloseDoesNotModifyFile(filename, wb);

        // File
        //wb = new XSSFWorkbook(file);
        //assertCloseDoesNotModifyFile(filename, wb);

        // InputStream
        try (FileInputStream is = new FileInputStream(file)) {
            wb = new XSSFWorkbook(is);
            assertCloseDoesNotModifyFile(filename, wb);
        }

        // OPCPackage
        //wb = new XSSFWorkbook(OPCPackage.open(file));
        //assertCloseDoesNotModifyFile(filename, wb);
    }

    @Test
    void testCloseBeforeWrite() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("somesheet");

            // test what happens if we close the Workbook before we write it out
            wb.close();

            assertThrows(IOException.class, () -> {
                try {
                    writeOutAndReadBack(wb);
                } catch (RuntimeException e) {
                    throw e.getCause();
                }
            });
        }
    }

    /**
     * See bug #57840 test data tables
     */
    @Test
    void getTable() throws IOException {
       XSSFWorkbook wb = openSampleWorkbook("WithTable.xlsx");
       XSSFTable table1 = wb.getTable("Tabella1");
       assertNotNull(table1, "Tabella1 was not found in workbook");
       assertEquals("Tabella1", table1.getName(), "Table name");
       assertEquals("Foglio1", table1.getSheetName(), "Sheet name");

       // Table lookup should be case-insensitive
       assertSame(table1, wb.getTable("TABELLA1"), "Case insensitive table name lookup");

       // If workbook does not contain any data tables matching the provided name, getTable should return null
       assertNull(wb.getTable(null), "Null table name should not throw NPE");
       assertNull(wb.getTable("Foglio1"), "Should not be able to find non-existent table");

       // If a table is added after getTable is called it should still be reachable by XSSFWorkbook.getTable
       // This test makes sure that if any caching is done that getTable never uses a stale cache
       XSSFTable table2 = wb.getSheet("Foglio2").createTable(null);
       table2.setName("Table2");
       assertSame(table2, wb.getTable("Table2"), "Did not find Table2");

       // If table name is modified after getTable is called, the table can only be found by its new name
       // This test makes sure that if any caching is done that getTable never uses a stale cache
       table1.setName("Table1");
       assertSame(table1, wb.getTable("TABLE1"), "Did not find Tabella1 renamed to Table1");

       wb.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    void testRemoveSheet() throws IOException {
        // Test removing a sheet maintains the named ranges correctly
        XSSFWorkbook wb = new XSSFWorkbook();
        wb.createSheet("Sheet1");
        wb.createSheet("Sheet2");

        XSSFName sheet1Name = wb.createName();
        sheet1Name.setNameName("name1");
        sheet1Name.setSheetIndex(0);
        sheet1Name.setRefersToFormula("Sheet1!$A$1");

        XSSFName sheet2Name = wb.createName();
        sheet2Name.setNameName("name1");
        sheet2Name.setSheetIndex(1);
        sheet2Name.setRefersToFormula("Sheet2!$A$1");

        assertTrue(wb.getAllNames().contains(sheet1Name));
        assertTrue(wb.getAllNames().contains(sheet2Name));

        assertEquals(2, wb.getNames("name1").size());
        assertEquals(sheet1Name, wb.getNames("name1").get(0));
        assertEquals(sheet2Name, wb.getNames("name1").get(1));

        // Remove sheet1, we should only have sheet2Name now
        wb.removeSheetAt(0);

        assertFalse(wb.getAllNames().contains(sheet1Name));
        assertTrue(wb.getAllNames().contains(sheet2Name));
        assertEquals(1, wb.getNames("name1").size());
        assertEquals(sheet2Name, wb.getNames("name1").get(0));

        // Check by index as well for sanity
        assertEquals(1, wb.getNumberOfNames());
        assertEquals(0, wb.getNameIndex("name1"));
        assertEquals(sheet2Name, wb.getNameAt(0));

        wb.close();
    }

    /**
     * See bug #61700
     */
    @Test
    void testWorkbookForceFormulaRecalculation() throws Exception {
        Workbook workbook = _testDataProvider.createWorkbook();
        workbook.createSheet().createRow(0).createCell(0).setCellFormula("B1+C1");
        workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();

        assertFalse(workbook.getForceFormulaRecalculation());
        workbook.setForceFormulaRecalculation(true);
        assertTrue(workbook.getForceFormulaRecalculation());

        Workbook wbBack = _testDataProvider.writeOutAndReadBack(workbook);
        assertTrue(wbBack.getForceFormulaRecalculation());
        wbBack.setForceFormulaRecalculation(false);
        assertFalse(wbBack.getForceFormulaRecalculation());

        Workbook wbBack2 = _testDataProvider.writeOutAndReadBack(wbBack);
        assertFalse(wbBack2.getForceFormulaRecalculation());

        workbook.close();
        wbBack.close();
        wbBack2.close();
    }

    @Test
    void testRightToLeft() throws IOException {
        try(XSSFWorkbook workbook = openSampleWorkbook("right-to-left.xlsx")){
            Sheet sheet = workbook.getSheet("عربى");

            Cell A1 = sheet.getRow(0).getCell(0);
            Cell A2 = sheet.getRow(1).getCell(0);
            Cell A3 = sheet.getRow(2).getCell(0);
            Cell A4 = sheet.getRow(3).getCell(0);

            expectFormattedContent(A1, "نص");
            expectFormattedContent(A2, "123"); //this should really be ۱۲۳
            expectFormattedContent(A3, "text with comment");
            expectFormattedContent(A4, " עִבְרִית and اَلْعَرَبِيَّةُ");

            Comment a3Comment = sheet.getCellComment(new CellAddress("A3"));
            assertTrue(a3Comment.getString().getString().contains("تعليق الاختبا"));
        }
    }

    private static void expectFormattedContent(Cell cell, String value) {
        assertEquals(value, new DataFormatter().formatCellValue(cell),
                "Cell " + ref(cell) + " has wrong formatted content.");
    }

    private static String ref(Cell cell) {
        return new CellReference(cell).formatAsString();
    }
}
