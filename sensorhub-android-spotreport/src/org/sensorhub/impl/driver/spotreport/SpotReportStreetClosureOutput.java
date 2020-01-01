/**************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 Copyright (C) 2019 Botts Innovative Research, Inc. All Rights Reserved.
 ******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.driver.spotreport;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.Quantity;
import net.opengis.swe.v20.Text;
import net.opengis.swe.v20.Time;
import net.opengis.swe.v20.Vector;

import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import org.vast.data.AbstractDataBlock;
import org.vast.data.DataBlockMixed;
import org.vast.swe.SWEHelper;
import org.vast.swe.helper.GeoPosHelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ResultReceiver;
import android.util.Log;

import java.util.UUID;

/**
 * <p>
 * Implementation of data interface for Spot Reports
 * </p>
 *
 * @author Nicolas Garay <nicolasgaray@icloud.com>
 * @since Nov 9, 2019
 */
public class SpotReportStreetClosureOutput extends AbstractSensorOutput<SpotReportDriver> {

    // keep logger name short because in LogCat it's max 23 chars
//    private static final Logger log = LoggerFactory.getLogger(SpotReportOutput.class.getSimpleName());

    // Data Associated with Broadcast Receivers and Intents
    private static final String ACTION_SUBMIT_STREET_CLOSURE_REPORT = "org.sensorhub.android.intent.SPOT_REPORT_STREET_CLOSURE";
    private static final int SUBMIT_REPORT_FAILURE = 0;
    private static final int SUBMIT_REPORT_SUCCESS = 1;

    private static final String DATA_LAT = "lat";
    private static final String DATA_LON = "lon";
    private static final String DATA_RADIUS = "radius";
    private static final String DATA_TYPE = "type";
    private static final String DATA_ACTION = "action";
    private static final String DATA_REFERENCE_ID = "reference";
    private SpotReportReceiver broadcastReceiver = new SpotReportReceiver();

    // SWE DataBlock elements
    private static final String DATA_RECORD_REPORT_TIME_LABEL = "time";
    private static final String DATA_RECORD_REPORT_ID_LABEL = "id";
    private static final String DATA_RECORD_REPORT_LOC_LABEL = "location";
    private static final String DATA_RECORD_REPORT_RADIUS_LABEL = "radius";
    private static final String DATA_RECORD_REPORT_TYPE_LABEL = "type";
    private static final String DATA_RECORD_REPORT_ACTION_LABEL = "action";
    private static final String DATA_RECORD_REPORT_REFERENCE_LABEL = "reference";

    private static final String DATA_RECORD_NAME = "Street Closure Spot Report";
    private static final String DATA_RECORD_DESCRIPTION =
            "A report generated by visual observance and classification which is accompanied by a" +
                    " location, description, and other data";
    private static final String DATA_RECORD_DEFINITION =
            SWEHelper.getPropertyUri("StreetClosureSpotReport");

    private DataComponent dataStruct;
    private DataEncoding dataEncoding;

    private Context context;
    private String name;

    SpotReportStreetClosureOutput(SpotReportDriver parentModule) {

        super(parentModule);
        this.name = parentModule.getName() + " Street Closure";
        context = getParentModule().getConfiguration().androidContext;
    }

    @Override
    public String getName() {

        return name;
    }

    /**
     * Initialize the output data structure
     *
     * @throws SensorException
     */
    void init() throws SensorException {

        // Build data structure ********************************************************************
        SWEHelper sweHelper = new SWEHelper();
        dataStruct = sweHelper.newDataRecord();
        dataStruct.setDescription(DATA_RECORD_DESCRIPTION);
        dataStruct.setDefinition(DATA_RECORD_DEFINITION);
        dataStruct.setName(DATA_RECORD_NAME);

        // Add time stamp component to data record
        Time time = sweHelper.newTimeStampIsoUTC();
        dataStruct.addComponent(DATA_RECORD_REPORT_TIME_LABEL, time);

        Text id = sweHelper.newText(SWEHelper.getPropertyUri("ID"),
                "ID",
                "A unique identifier for the report");
        dataStruct.addComponent(DATA_RECORD_REPORT_ID_LABEL, id);

        // Add the location component of the data record
        GeoPosHelper geoPosHelper = new GeoPosHelper();
        Vector locationVectorLLA = geoPosHelper.newLocationVectorLLA(null);
        locationVectorLLA.setLocalFrame(parentSensor.localFrameURI);
        dataStruct.addComponent(DATA_RECORD_REPORT_LOC_LABEL, locationVectorLLA);

        Quantity radius = sweHelper.newQuantity(SWEHelper.getPropertyUri("Radius"),
                "Radius",
                "Radius in ft. of reported location where observation may be contained or found",
                "ft.");
        dataStruct.addComponent(DATA_RECORD_REPORT_RADIUS_LABEL, radius);

        Text type = sweHelper.newText(SWEHelper.getPropertyUri("Type"),
                "Type",
                "A description of the type pf closer, all or public");
        dataStruct.addComponent(DATA_RECORD_REPORT_TYPE_LABEL, type);

        Text action = sweHelper.newText(SWEHelper.getPropertyUri("Action"),
                "Action",
                "The action associated with the closure event");
        dataStruct.addComponent(DATA_RECORD_REPORT_ACTION_LABEL, action);

        Text reference = sweHelper.newText(SWEHelper.getPropertyUri("Reference"),
                "Reference",
                "If not empty, denotes the associated observation for this event");
        dataStruct.addComponent(DATA_RECORD_REPORT_REFERENCE_LABEL, reference);

        // Setup data encoding *********************************************************************
        this.dataEncoding = sweHelper.newTextEncoding(",", "\n");
    }

    /**
     * Populate and submit an instance of the SpotReport containing no image.
     *
     * @param lat Latitude
     * @param lon Longitude
     * @param radius Radius of validity
     * @param type Describes the type of closure event
     * @param action Describes the action, close or open
     * @param reference Reference id of related observation
     */
    private void submitReport(String lat, String lon, int radius, String type,
                              String action, String reference) {

        double samplingTime = System.currentTimeMillis() / 1000.0;

        // generate new data record
        DataBlock newRecord;

        if (latestRecord == null) {

            newRecord = dataStruct.createDataBlock();
        } else {

            newRecord = latestRecord.renew();
        }

        newRecord.setDoubleValue(0, samplingTime);
        newRecord.setStringValue(1, UUID.randomUUID().toString());
        newRecord.setDoubleValue(2, Double.parseDouble(lat));
        newRecord.setDoubleValue(3, Double.parseDouble(lon));
        newRecord.setDoubleValue(4, 0.0);
        newRecord.setIntValue(5, radius);
        newRecord.setStringValue(6, type);
        newRecord.setStringValue(7, action);
        newRecord.setStringValue(8, reference);

        // update latest record and send event
        latestRecord = newRecord;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, this, newRecord));
    }

    public void start() {

        context.registerReceiver(broadcastReceiver, new IntentFilter(ACTION_SUBMIT_STREET_CLOSURE_REPORT));
    }

    @Override
    public void stop() {

        context.unregisterReceiver(broadcastReceiver);
    }

    @Override
    public double getAverageSamplingPeriod() {

        return 1;
    }

    @Override
    public DataComponent getRecordDescription() {

        return dataStruct;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {

        return dataEncoding;
    }

    @Override
    public DataBlock getLatestRecord() {

        return latestRecord;
    }

    @Override
    public long getLatestRecordTime() {

        return latestRecordTime;
    }

    /**
     * Broadcast receiver to register with OS for IPC with SpotReportActivity
     */
    private class SpotReportReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            ResultReceiver resultReceiver = intent.getParcelableExtra(Intent.EXTRA_RESULT_RECEIVER);

            try {

                if (ACTION_SUBMIT_STREET_CLOSURE_REPORT.equals(intent.getAction())) {

                    String lat = intent.getStringExtra(DATA_LAT);
                    String lon = intent.getStringExtra(DATA_LON);
                    int radius = intent.getIntExtra(DATA_RADIUS, 0);
                    String type = intent.getStringExtra(DATA_TYPE);
                    String action = intent.getStringExtra(DATA_ACTION);
                    String reference = intent.getStringExtra(DATA_REFERENCE_ID);

                    if(null == reference) {

                        reference = "";
                    }

                    submitReport(lat, lon, radius, type, action, reference);

                    resultReceiver.send(SUBMIT_REPORT_SUCCESS, null);

                }

            } catch (Exception e) {

                Log.e("SpotReportOutput", e.toString());
                resultReceiver.send(SUBMIT_REPORT_FAILURE, null);
            }
        }
    }
}
