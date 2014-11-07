/*
 * Licensed to STRATIO (C) under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  The STRATIO (C) licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.stratio.crossdata.sh.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.xml.sax.SAXException;

import com.stratio.crossdata.common.manifest.CrossdataManifest;
import com.stratio.crossdata.common.data.Cell;
import com.stratio.crossdata.common.data.ResultSet;
import com.stratio.crossdata.common.data.Row;
import com.stratio.crossdata.common.exceptions.ManifestException;
import com.stratio.crossdata.common.metadata.ColumnMetadata;
import com.stratio.crossdata.common.result.CommandResult;
import com.stratio.crossdata.common.result.ConnectResult;
import com.stratio.crossdata.common.result.MetadataResult;
import com.stratio.crossdata.common.result.QueryResult;
import com.stratio.crossdata.common.result.StorageResult;
import com.stratio.crossdata.common.manifest.ConnectorFactory;
import com.stratio.crossdata.common.manifest.ConnectorType;
import com.stratio.crossdata.common.manifest.DataStoreFactory;
import com.stratio.crossdata.common.manifest.DataStoreType;
import com.stratio.crossdata.common.result.ErrorResult;
import com.stratio.crossdata.common.result.Result;

import jline.console.ConsoleReader;
import jline.console.history.History;
import jline.console.history.MemoryHistory;

/**
 * Utility class for console related operations.
 */
public final class ConsoleUtils {

    /**
     * Class logger.
     */
    private static final Logger LOG = Logger.getLogger(ConsoleUtils.class);

    /**
     * Number of days a history entry is maintained.
     */
    private static final int DAYS_HISTORY_ENTRY_VALID = 30;

    private static final String DATASTORE_SCHEMA_PATH = "/com/stratio/crossdata/connector/DataStoreDefinition.xsd";
    private static final String CONNECTOR_SCHEMA_PATH = "/com/stratio/crossdata/connector/ConnectorDefinition.xsd";

    /**
     * Private class constructor as all methods are static.
     */
    private ConsoleUtils() {
    }

    /**
     * Convert Result {@link com.stratio.crossdata.common.result.Result} structure to String.
     *
     * @param result {@link com.stratio.crossdata.common.result.Result} from execution.
     * @return String representing the result.
     */
    public static String stringResult(Result result) {
        if (ErrorResult.class.isInstance(result)) {
            return ErrorResult.class.cast(result).getErrorMessage();
        }
        if (result instanceof QueryResult) {
            QueryResult queryResult = (QueryResult) result;
            return stringQueryResult(queryResult);
        } else if (result instanceof CommandResult) {
            CommandResult commandResult = (CommandResult) result;
            return String.class.cast(commandResult.getResult());
        } else if (result instanceof ConnectResult) {
            ConnectResult connectResult = (ConnectResult) result;
            return String.valueOf("Connected with SessionId=" + connectResult.getSessionId());
        } else if (result instanceof MetadataResult) {
            MetadataResult metadataResult = (MetadataResult) result;
            return metadataResult.toString();
        } else if (result instanceof StorageResult) {
            StorageResult storageResult = (StorageResult) result;
            return storageResult.toString();
        } else {
            return "Unknown result";
        }
    }

    /**
     * Convert QueryResult {@link com.stratio.crossdata.common.result.QueryResult} structure to String.
     *
     * @param queryResult {@link com.stratio.crossdata.common.result.QueryResult} from execution.
     * @return String representing the result.
     */

    private static String stringQueryResult(QueryResult queryResult) {
        if (queryResult.getResultSet().isEmpty()) {
            return System.lineSeparator() + "OK";
        }

        ResultSet resultSet = queryResult.getResultSet();

        Map<String, Integer> colWidths = calculateColWidths(resultSet);

        String bar =
                StringUtils.repeat('-', getTotalWidth(colWidths) + (colWidths.values().size() * 3) + 1);

        StringBuilder sb = new StringBuilder(System.lineSeparator());
        sb.append("Partial result: ");
        sb.append(!queryResult.isLastResultSet());
        sb.append(System.lineSeparator());
        sb.append(bar).append(System.lineSeparator());
        sb.append("| ");
        for (ColumnMetadata columnMetadata: resultSet.getColumnMetadata()) {
            sb.append(
                    StringUtils.rightPad(columnMetadata.getName().getColumnNameToShow(),
                            colWidths.get(columnMetadata.getName().getColumnNameToShow()) + 1)).append("| ");
        }

        sb.append(System.lineSeparator());
        sb.append(bar);
        sb.append(System.lineSeparator());

        for (Row row : resultSet) {
            sb.append("| ");
            for (Map.Entry<String, Cell> entry : row.getCells().entrySet()) {
                String str = String.valueOf(entry.getValue().getValue());
                sb.append(StringUtils.rightPad(str, colWidths.get(entry.getKey())));
                sb.append(" | ");
            }
            sb.append(System.lineSeparator());
        }
        sb.append(bar).append(System.lineSeparator());
        return sb.toString();
    }

    /**
     * In order to print the result, this method calculates the maximum width of every column.
     *
     * @param resultSet structure representing the result of a execution.
     * @return Map<String, Integer> where the key is the name of the column and Integer is the maximum
     * width.
     */
    private static Map<String, Integer> calculateColWidths(ResultSet resultSet) {
        Map<String, Integer> colWidths = new HashMap<>();

        // Get column names or aliases width
        for (ColumnMetadata columnMetadata: resultSet.getColumnMetadata()) {
            colWidths.put(columnMetadata.getName().getName(),
                    columnMetadata.getName().getColumnNameToShow().length());
        }

        // Find widest cell content of every column
        for (Row row: resultSet) {
            for (String key: row.getCells().keySet()) {
                String cellContent = String.valueOf(row.getCell(key).getValue());
                int currentWidth = colWidths.get(key);
                if (cellContent.length() > currentWidth) {
                    colWidths.put(key, cellContent.length());
                }
            }
        }

        return colWidths;
    }

    /**
     * In order to create a separator line in tables, this method calculates the total width of a
     * table.
     *
     * @param colWidths columns widths of a table.
     * @return total width of a table.
     */
    private static int getTotalWidth(Map<String, Integer> colWidths) {
        int totalWidth = 0;
        for (int width : colWidths.values()) {
            totalWidth += width;
        }
        return totalWidth;
    }

    /**
     * Get previous history of the Crossdata console from a file.
     *
     * @param console Crossdata console created from a JLine console
     * @param sdf     Simple Date Format to read dates from history file
     * @return File inserted in the JLine console with the previous history
     * @throws IOException
     */
    public static File retrieveHistory(ConsoleReader console, SimpleDateFormat sdf)
            throws IOException {
        Date today = new Date();
        String workingDir = System.getProperty("user.home");
        File dir = new File(workingDir, ".com.stratio.crossdata");
        if (!dir.exists() && !dir.mkdir()) {
            LOG.error("Cannot create history directory: " + dir.getAbsolutePath());
        }
        File file = new File(dir.getPath() + "/history.txt");
        if (!file.exists() && !file.createNewFile()) {
            LOG.error("Cannot create history file: " + file.getAbsolutePath());
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Retrieving history from " + file.getAbsolutePath());
        }
        BufferedReader br =
                new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        History oldHistory = new MemoryHistory();
        DateTime todayDate = new DateTime(today);
        String line;
        String[] lineArray;
        Date lineDate;
        String lineStatement;
        try {
            while ((line = br.readLine()) != null) {
                lineArray = line.split("\\|");
                lineDate = sdf.parse(lineArray[0]);
                if (Days.daysBetween(new DateTime(lineDate), todayDate).getDays() < DAYS_HISTORY_ENTRY_VALID) {
                    lineStatement = lineArray[1];
                    oldHistory.add(lineStatement);
                }
            }
        } catch (ParseException ex) {
            LOG.error("Cannot parse date", ex);
        } catch (Exception ex) {
            LOG.error("Cannot read all the history", ex);
        } finally {
            br.close();
        }
        console.setHistory(oldHistory);
        LOG.info("History retrieved");
        return file;
    }

    /**
     * This method save history extracted from the Crossdata console to be persisted in the disk.
     *
     * @param console Crossdata console created from a JLine console
     * @param file    represents the file to be created of updated with the statements from the current
     *                session
     * @param sdf     Simple Date Format to create dates for the history file
     * @throws IOException file couldn't be created or read
     */
    public static void saveHistory(ConsoleReader console, File file, SimpleDateFormat sdf)
            throws IOException {
        boolean created = file.createNewFile();
        OutputStreamWriter isr;
        if (created) {
            isr = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        } else {
            isr = new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8");
        }
        try (BufferedWriter bufferWriter = new BufferedWriter(isr)) {
            History history = console.getHistory();
            ListIterator<History.Entry> histIter = history.entries();
            while (histIter.hasNext()) {
                History.Entry entry = histIter.next();
                bufferWriter.write(sdf.format(new Date()));
                bufferWriter.write("|");
                bufferWriter.write(entry.value().toString());
                bufferWriter.newLine();
            }
            bufferWriter.flush();
        }
    }

    /**
     * Parse an XML document into a {@link com.stratio.crossdata.common.manifest.CrossdataManifest}.
     * @param manifestType The type of manifest.
     * @param path The XML path.
     * @return A {@link com.stratio.crossdata.common.manifest.CrossdataManifest}.
     * @throws ManifestException If the XML is not valid.
     * @throws FileNotFoundException If the XML file does not exists.
     */
    public static CrossdataManifest parseFromXmlToManifest(int manifestType, String path) throws
            ManifestException, FileNotFoundException {
        if (manifestType == CrossdataManifest.TYPE_DATASTORE) {
            return parseFromXmlToDataStoreManifest(new FileInputStream(path));
        } else {
            return parseFromXmlToConnectorManifest(new FileInputStream(path));
        }
    }

    /**
     * Parse an XML document into a {@link com.stratio.crossdata.common.manifest.CrossdataManifest}.
     * @param manifestType The type of manifest.
     * @param path The {@link java.io.InputStream} to retrieve the XML.
     * @return A {@link com.stratio.crossdata.common.manifest.CrossdataManifest}.
     * @throws ManifestException If the XML is not valid.
     */
    public static CrossdataManifest parseFromXmlToManifest(int manifestType, InputStream path) throws
            ManifestException {
        if (manifestType == CrossdataManifest.TYPE_DATASTORE) {
            return parseFromXmlToDataStoreManifest(path);
        } else {
            return parseFromXmlToConnectorManifest(path);
        }
    }

    private static DataStoreType parseFromXmlToDataStoreManifest(InputStream path)
            throws ManifestException {
        try {
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

            Schema schema = sf.newSchema(ConsoleUtils.class.getResource(DATASTORE_SCHEMA_PATH));

            JAXBContext jaxbContext = JAXBContext.newInstance(DataStoreFactory.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

            unmarshaller.setSchema(schema);

            JAXBElement<DataStoreType> unmarshalledDataStore = (JAXBElement<DataStoreType>) unmarshaller
                    .unmarshal(path);
            return unmarshalledDataStore.getValue();
        } catch (JAXBException | SAXException e) {
            throw new ManifestException(e);
        }
    }

    private static CrossdataManifest parseFromXmlToConnectorManifest(InputStream path) throws ManifestException {
        try {
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = sf.newSchema(ConsoleUtils.class.getResource(CONNECTOR_SCHEMA_PATH));

            JAXBContext jaxbContext = JAXBContext.newInstance(ConnectorFactory.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

            unmarshaller.setSchema(schema);

            JAXBElement<ConnectorType> unmarshalledDataStore = (JAXBElement<ConnectorType>) unmarshaller
                    .unmarshal(path);
            return unmarshalledDataStore.getValue();
        } catch (JAXBException | SAXException e) {
            throw new ManifestException(e);
        }
    }
}
