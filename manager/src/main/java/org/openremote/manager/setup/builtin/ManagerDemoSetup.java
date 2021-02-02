/*
 * Copyright 2016, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.manager.setup.builtin;

import org.openremote.agent.protocol.http.HttpClientAgent;
import org.openremote.agent.protocol.simulator.SimulatorAgent;
import org.openremote.container.util.UniqueIdentifierGenerator;
import org.openremote.manager.setup.AbstractManagerSetup;
import org.openremote.model.Constants;
import org.openremote.model.Container;
import org.openremote.model.apps.ConsoleAppConfig;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.impl.*;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeLink;
import org.openremote.model.attribute.AttributeRef;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.geo.GeoJSONPoint;
import org.openremote.model.security.Tenant;
import org.openremote.model.simulator.SimulatorReplayDatapoint;
import org.openremote.model.value.JsonPathFilter;
import org.openremote.model.value.ValueConstraint;
import org.openremote.model.value.ValueFilter;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Random;

import static java.time.temporal.ChronoField.SECOND_OF_DAY;
import static org.openremote.model.value.MetaItemType.ATTRIBUTE_LINKS;
import static org.openremote.model.value.MetaItemType.*;
import static org.openremote.model.value.ValueType.*;

public class ManagerDemoSetup extends AbstractManagerSetup {

    public static GeoJSONPoint STATIONSPLEIN_LOCATION = new GeoJSONPoint(4.470175, 51.923464);
    public String masterRealm;
    public String realmCityTenant;
    public String area1Id;
    public String smartcitySimulatorAgentId;
    public String energyManagementId;
    public String weatherHttpApiAgentId;

    private final long halfHourInMillis = Duration.ofMinutes(30).toMillis();

    public ManagerDemoSetup(Container container) {
        super(container);
    }

    private static int getRandomNumberInRange(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        Random r = new Random();
        return r.nextInt((max - min) + 1) + min;
    }

    @Override
    public void onStart() throws Exception {

        KeycloakDemoSetup keycloakDemoSetup = setupService.getTaskOfType(KeycloakDemoSetup.class);
        Tenant masterTenant = keycloakDemoSetup.masterTenant;
        Tenant tenantCity = keycloakDemoSetup.tenantCity;
        masterRealm = masterTenant.getRealm();
        this.realmCityTenant = tenantCity.getRealm();

        // ################################ Demo assets for 'master' realm ###################################


        // ################################ Link demo users and assets ###################################


        // ################################ Make users restricted ###################################


        // ################################ Realm smartcity ###################################

        SimulatorAgent smartcitySimulatorAgent = new SimulatorAgent("Simulator agent");
        smartcitySimulatorAgent.setRealm(this.realmCityTenant);

        smartcitySimulatorAgent = assetStorageService.merge(smartcitySimulatorAgent);
        smartcitySimulatorAgentId = smartcitySimulatorAgent.getId();

        LocalTime midnight = LocalTime.of(0, 0);

        // ################################ Realm smartcity - Energy Management ###################################

        ThingAsset energyManagement = new ThingAsset("Energy management");
        energyManagement.setRealm(this.realmCityTenant);
        energyManagement.getAttributes().addOrReplace(
                new Attribute<>("powerTotalProducers", NUMBER)
                    .addOrReplaceMeta(
                        new MetaItem<>(LABEL, "Combined power of all producers"),
                        new MetaItem<>(UNITS, Constants.units(Constants.UNITS_KILO, Constants.UNITS_WATT)),
                        new MetaItem<>(READ_ONLY, true)),
                new Attribute<>("powerTotalConsumers", NUMBER).addOrReplaceMeta(
                        new MetaItem<>(LABEL, "Combined power use of all consumers"),
                        new MetaItem<>(UNITS, Constants.units(Constants.UNITS_KILO, Constants.UNITS_WATT)),
                        new MetaItem<>(READ_ONLY, true))
        );
        energyManagement.setId(UniqueIdentifierGenerator.generateId(energyManagement.getName()));
        energyManagement = assetStorageService.merge(energyManagement);
        energyManagementId = energyManagement.getId();

        // ### De Rotterdam ###
        BuildingAsset building1Asset = new BuildingAsset("De Rotterdam");
        building1Asset.setParent(energyManagement);
        building1Asset.getAttributes().addOrReplace(
                new Attribute<>(BuildingAsset.STREET, "Wilhelminakade 139"),
                new Attribute<>(BuildingAsset.POSTAL_CODE, "3072 AP"),
                new Attribute<>(BuildingAsset.CITY, "Rotterdam"),
                new Attribute<>(BuildingAsset.COUNTRY, "Netherlands"),
                new Attribute<>(Asset.LOCATION, new GeoJSONPoint(4.488324, 51.906577)),
                new Attribute<>("powerBalance", NUMBER).addMeta(
                        new MetaItem<>(LABEL, "Balance of power production and use"),
                        new MetaItem<>(UNITS, Constants.units(Constants.UNITS_KILO, Constants.UNITS_WATT)),
                        new MetaItem<>(READ_ONLY))
        );
        building1Asset.setId(UniqueIdentifierGenerator.generateId(building1Asset.getName() + "building"));
        building1Asset = assetStorageService.merge(building1Asset);

        ElectricityStorageAsset storage1Asset = createDemoElectricityStorageAsset("Battery De Rotterdam", building1Asset, new GeoJSONPoint(4.488324, 51.906577));
        storage1Asset.setManufacturer("Super-B");
        storage1Asset.setModel("Nomia");
        storage1Asset.setId(UniqueIdentifierGenerator.generateId(storage1Asset.getName()));
        storage1Asset = assetStorageService.merge(storage1Asset);

        ElectricityConsumerAsset consumption1Asset = createDemoElectricityConsumerAsset("Consumption De Rotterdam", building1Asset, new GeoJSONPoint(4.487519, 51.906544));
        consumption1Asset.getAttribute(ElectricityConsumerAsset.POWER).ifPresent(assetAttribute -> {
                assetAttribute.addMeta(
                    new MetaItem<>(
                        AGENT_LINK,
                        new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                            new SimulatorReplayDatapoint[]{
                                new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 23),
                                new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 21),
                                new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 20),
                                new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 22),
                                new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 21),
                                new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 22),
                                new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 41),
                                new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 54),
                                new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 63),
                                new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 76),
                                new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 80),
                                new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 79),
                                new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 84),
                                new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 76),
                                new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 82),
                                new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 83),
                                new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 77),
                                new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 71),
                                new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 63),
                                new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 41),
                                new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 27),
                                new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 22),
                                new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 24),
                                new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 20)
                            })
                    )
                );
        });

        consumption1Asset.setId(UniqueIdentifierGenerator.generateId(consumption1Asset.getName()));
        consumption1Asset = assetStorageService.merge(consumption1Asset);

        ElectricityProducerSolarAsset production1Asset = createDemoElectricitySolarProducerAsset("Solar De Rotterdam", building1Asset, new GeoJSONPoint(4.488592, 51.907047));
        production1Asset.setManufacturer("AEG");
        production1Asset.setModel("AS-P60");
        production1Asset.getAttribute(ElectricityProducerAsset.POWER).ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem<>(
                            AGENT_LINK,
                            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                new SimulatorReplayDatapoint[] {
                                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 1),
                                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 10),
                                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 15),
                                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 39),
                                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 52),
                                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 50),
                                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 48),
                                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 36),
                                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 23),
                                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 24),
                                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 18),
                                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 10),
                                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 8),
                                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 1),
                                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 0)
                                }
                            )
                    )
            );
        });
        production1Asset.setEnergyExportTotal(152689d);
        production1Asset.setPowerExportMax(89.6);
        production1Asset.setEfficiencyExport(93);
        production1Asset.setPanelOrientation(ElectricityProducerSolarAsset.PanelOrientation.EAST_WEST);
        production1Asset.setId(UniqueIdentifierGenerator.generateId(production1Asset.getName()));
        production1Asset = assetStorageService.merge(production1Asset);

        // ### Stadhuis ###

        BuildingAsset building2Asset = new BuildingAsset("Stadhuis");
        building2Asset.setParent(energyManagement);
        building2Asset.getAttributes().addOrReplace(
                new Attribute<>(BuildingAsset.STREET, "Coolsingel 40"),
                new Attribute<>(BuildingAsset.POSTAL_CODE, "3011 AD"),
                new Attribute<>(BuildingAsset.CITY, "Rotterdam"),
                new Attribute<>(BuildingAsset.COUNTRY, "Netherlands"),
                new Attribute<>(Asset.LOCATION, new GeoJSONPoint(4.47985, 51.92274))
        );
        building2Asset.setId(UniqueIdentifierGenerator.generateId(building2Asset.getName() + "building"));
        building2Asset = assetStorageService.merge(building2Asset);

        ElectricityStorageAsset storage2Asset = createDemoElectricityStorageAsset("Battery Stadhuis", building2Asset, new GeoJSONPoint(4.47985, 51.92274));
        storage2Asset.setManufacturer("LG Chem");
        storage2Asset.setModel("ESS Industrial");
        storage2Asset.setId(UniqueIdentifierGenerator.generateId(storage2Asset.getName()));
        storage2Asset = assetStorageService.merge(storage2Asset);

        ElectricityConsumerAsset consumption2Asset = createDemoElectricityConsumerAsset("Consumption Stadhuis", building2Asset, new GeoJSONPoint(4.47933, 51.92259));
        consumption2Asset.getAttribute(ElectricityConsumerAsset.POWER).ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem<>(
                            AGENT_LINK,
                            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                new SimulatorReplayDatapoint[] {
                                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 7),
                                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 8),
                                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 7),
                                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 9),
                                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 8),
                                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 9),
                                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 12),
                                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 22),
                                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 30),
                                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 36),
                                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 39),
                                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 32),
                                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 36),
                                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 44),
                                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 47),
                                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 44),
                                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 38),
                                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 38),
                                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 34),
                                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 33),
                                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 23),
                                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 13),
                                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 9),
                                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 8)
                                }
                            )
                    )
            );
        });
        consumption2Asset.setId(UniqueIdentifierGenerator.generateId(consumption2Asset.getName()));
        consumption2Asset = assetStorageService.merge(consumption2Asset);

        ElectricityProducerSolarAsset production2Asset = createDemoElectricitySolarProducerAsset("Solar Stadhuis", building2Asset, new GeoJSONPoint(4.47945, 51.92301));
        production2Asset.getAttribute(ElectricityProducerAsset.POWER).ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem<>(
                            AGENT_LINK,
                            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                new SimulatorReplayDatapoint[] {
                                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 1),
                                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 2),
                                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 8),
                                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 14),
                                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 12),
                                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 10),
                                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 7),
                                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 5),
                                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 7),
                                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 5),
                                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 2),
                                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 1),
                                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 1),
                                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 0)
                                }
                            )
                    )
            );
        });
        production2Asset.setEnergyExportTotal(88961d);
        production2Asset.setPowerExportMax(19.2);
        production2Asset.setEfficiencyExport(79);
        production2Asset.setPanelOrientation(ElectricityProducerSolarAsset.PanelOrientation.SOUTH);
        production2Asset.setManufacturer("Solarwatt");
        production2Asset.setModel("EasyIn 60M");
        production2Asset.setId(UniqueIdentifierGenerator.generateId(production2Asset.getName()));
        production2Asset = assetStorageService.merge(production2Asset);

        // ### Markthal ###

        BuildingAsset building3Asset = new BuildingAsset("Markthal");
        building3Asset.setParent(energyManagement);
        building3Asset.getAttributes().addOrReplace(
                new Attribute<>(BuildingAsset.STREET, "Dominee Jan Scharpstraat 298"),
                new Attribute<>(BuildingAsset.POSTAL_CODE, "3011 GZ"),
                new Attribute<>(BuildingAsset.CITY, "Rotterdam"),
                new Attribute<>(BuildingAsset.COUNTRY, "Netherlands"),
                new Attribute<>(Asset.LOCATION, new GeoJSONPoint(4.47945, 51.92301)),
                new Attribute<>("allChargersInUse", BOOLEAN)
                        .addMeta(
                                new MetaItem<>(LABEL, "All chargers in use"),
                                new MetaItem<>(READ_ONLY))
        );
        building3Asset.setId(UniqueIdentifierGenerator.generateId(building3Asset.getName() + "building"));
        building3Asset = assetStorageService.merge(building3Asset);

        ElectricityProducerSolarAsset production3Asset = createDemoElectricitySolarProducerAsset("Solar Markthal", building3Asset, new GeoJSONPoint(4.47945, 51.92301));
        production3Asset.getAttribute(ElectricityProducerAsset.POWER).ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                new MetaItem<>(
                        AGENT_LINK,
                        new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                            new SimulatorReplayDatapoint[] {
                                new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 0),
                                new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 0),
                                new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 0),
                                new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 0),
                                new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 0),
                                new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 0),
                                new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 0),
                                new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 0),
                                new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 2),
                                new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 6),
                                new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 10),
                                new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 13),
                                new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 21),
                                new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 14),
                                new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 17),
                                new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 10),
                                new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 9),
                                new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 7),
                                new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 5),
                                new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 4),
                                new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 2),
                                new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 1),
                                new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 0),
                                new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 0)
                            }
                        )
                )
            );
        });
        production3Asset.setEnergyExportTotal(24134d);
        production3Asset.setPowerExportMax(29.8);
        production3Asset.setEfficiencyExport(91);
        production3Asset.setPanelOrientation(ElectricityProducerSolarAsset.PanelOrientation.SOUTH);
        production3Asset.setManufacturer("Sunpower");
        production3Asset.setModel("E20-327");
        production3Asset.setId(UniqueIdentifierGenerator.generateId(production3Asset.getName()));
        production3Asset = assetStorageService.merge(production3Asset);

        ElectricityChargerAsset charger1Asset = createDemoElectricityChargerAsset("Charger 1 Markthal", building3Asset, new GeoJSONPoint(4.486143, 51.920058));
        charger1Asset.setPower(0d);
        charger1Asset.getAttributes().getOrCreate(ElectricityChargerAsset.POWER).addMeta(
                    new MetaItem<>(
                            AGENT_LINK,
                            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                new SimulatorReplayDatapoint[] {
                                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 2),
                                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 5),
                                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 10),
                                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 5),
                                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 15),
                                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 32),
                                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 35),
                                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 17),
                                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 9),
                                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 6),
                                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 0)
                                }
                            )
                    )
            );
        charger1Asset.setManufacturer("Allego");
        charger1Asset.setModel("HPC");
        charger1Asset.setId(UniqueIdentifierGenerator.generateId(charger1Asset.getName()));
        charger1Asset = assetStorageService.merge(charger1Asset);

        ElectricityChargerAsset charger2Asset = createDemoElectricityChargerAsset("Charger 2 Markthal", building3Asset, new GeoJSONPoint(4.486188, 51.919957));
        charger2Asset.setPower(0d);
        charger2Asset.getAttributes().getOrCreate(ElectricityChargerAsset.POWER)
            .addMeta(
                    new MetaItem<>(
                            AGENT_LINK,
                            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                new SimulatorReplayDatapoint[] {
                                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 5),
                                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 11),
                                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 5),
                                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 10),
                                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 6),
                                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 17),
                                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 14),
                                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 9),
                                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 4),
                                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 28),
                                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 38),
                                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 32),
                                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 26),
                                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 13),
                                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 6),
                                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 0)
                                }
                            )
                    )
            );
        charger2Asset.setManufacturer("Bosch");
        charger2Asset.setModel("EV800");
        charger2Asset.setId(UniqueIdentifierGenerator.generateId(charger2Asset.getName()));
        charger2Asset = assetStorageService.merge(charger2Asset);

        ElectricityChargerAsset charger3Asset = createDemoElectricityChargerAsset("Charger 3 Markthal", building3Asset, new GeoJSONPoint(4.486232, 51.919856));
        charger3Asset.setPower(0d);
        charger3Asset.getAttributes().getOrCreate(ElectricityChargerAsset.POWER)
            .addMeta(
                    new MetaItem<>(
                            AGENT_LINK,
                            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                new SimulatorReplayDatapoint[] {
                                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 4),
                                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 4),
                                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 7),
                                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 9),
                                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 6),
                                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 2),
                                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 6),
                                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 18),
                                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 4),
                                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 29),
                                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 34),
                                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 22),
                                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 14),
                                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 0)
                                }
                            )
                    )
            );
        charger3Asset.setManufacturer("Siemens");
        charger3Asset.setModel("CPC 50");
        charger3Asset.setId(UniqueIdentifierGenerator.generateId(charger3Asset.getName()));
        charger3Asset = assetStorageService.merge(charger3Asset);

        ElectricityChargerAsset charger4Asset = createDemoElectricityChargerAsset("Charger 4 Markthal", building3Asset, new GeoJSONPoint(4.486286, 51.919733));
        charger4Asset.setPower(0d);
        charger4Asset.getAttributes().getOrCreate(ElectricityChargerAsset.POWER)
            .addMeta(
                    new MetaItem<>(
                            AGENT_LINK,
                            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                new SimulatorReplayDatapoint[] {
                                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 4),
                                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 17),
                                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 15),
                                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 8),
                                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 16),
                                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 4),
                                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 15),
                                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 34),
                                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 30),
                                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 11),
                                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 16),
                                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 7),
                                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 4)
                                }
                            )
                    )
            );

        charger4Asset.setManufacturer("SemaConnect");
        charger4Asset.setModel("The Series 6");
        charger4Asset.setId(UniqueIdentifierGenerator.generateId(charger4Asset.getName()));
        charger4Asset = assetStorageService.merge(charger4Asset);

        // ### Erasmianum ###

        BuildingAsset building4Asset = new BuildingAsset("Erasmianum");
        building4Asset.setParent(energyManagement);
        building4Asset.getAttributes().addOrReplace(
                new Attribute<>(BuildingAsset.STREET, "Wytemaweg 25"),
                new Attribute<>(BuildingAsset.POSTAL_CODE, "3015 CN"),
                new Attribute<>(BuildingAsset.CITY, "Rotterdam"),
                new Attribute<>(BuildingAsset.COUNTRY, "Netherlands"),
                new Attribute<>(Asset.LOCATION, new GeoJSONPoint(4.468324, 51.912062))
        );
        building4Asset.setId(UniqueIdentifierGenerator.generateId(building4Asset.getName() + "building"));
        building4Asset = assetStorageService.merge(building4Asset);

        ElectricityConsumerAsset consumption4Asset = createDemoElectricityConsumerAsset("Consumption Erasmianum", building4Asset, new GeoJSONPoint(4.468324, 51.912062));
        consumption4Asset.getAttribute(ElectricityConsumerAsset.POWER).ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem<>(
                            AGENT_LINK,
                            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                new SimulatorReplayDatapoint[] {
                                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 6),
                                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 5),
                                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 6),
                                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 7),
                                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 5),
                                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 6),
                                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 9),
                                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 23),
                                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 37),
                                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 41),
                                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 47),
                                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 49),
                                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 51),
                                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 43),
                                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 48),
                                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 45),
                                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 46),
                                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 41),
                                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 38),
                                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 30),
                                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 19),
                                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 15),
                                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 7),
                                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 6)
                                }
                            )
                    )
            );
        });
        consumption4Asset.setId(UniqueIdentifierGenerator.generateId(consumption4Asset.getName()));
        consumption4Asset = assetStorageService.merge(consumption4Asset);

        // ### Oostelijk zwembad ###

        BuildingAsset building5Asset = new BuildingAsset("Oostelijk zwembad");
        building5Asset.setParent(energyManagement);
        building5Asset.getAttributes().addOrReplace(
                new Attribute<>(BuildingAsset.STREET, "Gerdesiaweg 480"),
                new Attribute<>(BuildingAsset.POSTAL_CODE, "3061 RA"),
                new Attribute<>(BuildingAsset.CITY, "Rotterdam"),
                new Attribute<>(BuildingAsset.COUNTRY, "Netherlands"),
                new Attribute<>(Asset.LOCATION, new GeoJSONPoint(4.498048, 51.925770))
        );
        building5Asset.setId(UniqueIdentifierGenerator.generateId(building5Asset.getName() + "building"));
        building5Asset = assetStorageService.merge(building5Asset);

        ElectricityConsumerAsset consumption5Asset = createDemoElectricityConsumerAsset("Consumption Zwembad", building5Asset, new GeoJSONPoint(4.498048, 51.925770));
        consumption5Asset.getAttribute(ElectricityConsumerAsset.POWER).ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem<>(
                            AGENT_LINK,
                            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                new SimulatorReplayDatapoint[] {
                                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 16),
                                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 16),
                                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 15),
                                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 16),
                                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 17),
                                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 16),
                                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 24),
                                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 35),
                                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 32),
                                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 33),
                                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 34),
                                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 33),
                                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 34),
                                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 31),
                                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 36),
                                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 34),
                                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 32),
                                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 37),
                                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 38),
                                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 37),
                                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 38),
                                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 35),
                                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 24),
                                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 19)
                                }
                            )
                    )
            );
        });
        consumption5Asset.setId(UniqueIdentifierGenerator.generateId(consumption5Asset.getName()));
        consumption5Asset = assetStorageService.merge(consumption5Asset);

        ElectricityProducerSolarAsset production5Asset = createDemoElectricitySolarProducerAsset("Solar Zwembad", building5Asset, new GeoJSONPoint(4.498281, 51.925507));
        production5Asset.getAttribute(ElectricityProducerAsset.POWER).ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem<>(
                            AGENT_LINK,
                            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                new SimulatorReplayDatapoint[] {
                                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 1),
                                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 8),
                                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 30),
                                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 44),
                                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 42),
                                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 41),
                                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 29),
                                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 19),
                                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 16),
                                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 11),
                                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 4),
                                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 2),
                                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 0)
                                }
                            )
                    )
            );
        });
        production5Asset.setEnergyExportTotal(23461d);
        production5Asset.setPowerExportMax(76.2);
        production5Asset.setEfficiencyExport(86);
        production5Asset.setPanelOrientation(ElectricityProducerSolarAsset.PanelOrientation.SOUTH);
        production5Asset.setManufacturer("S-Energy");
        production5Asset.setModel("SN260P-10");
        production5Asset.setId(UniqueIdentifierGenerator.generateId(production5Asset.getName()));
        production5Asset = assetStorageService.merge(production5Asset);

        // ### Weather ###
        HttpClientAgent weatherHttpApiAgent = new HttpClientAgent("Weather Agent");
        weatherHttpApiAgent.setParent(energyManagement);
        weatherHttpApiAgent.setBaseURI("https://api.openweathermap.org/data/2.5/");

        MultivaluedStringMap queryParams = new MultivaluedStringMap();
        queryParams.put("appid", Collections.singletonList("a6ea6724e5d116ea6d938bee2a8f4689"));
        queryParams.put("lat", Collections.singletonList("51.918849"));
        queryParams.put("lon", Collections.singletonList("4.463250"));
        queryParams.put("units", Collections.singletonList("metric"));
        weatherHttpApiAgent.setRequestQueryParameters(queryParams);

        MultivaluedStringMap headers = new MultivaluedStringMap();
        headers.put("Accept", Collections.singletonList("application/json"));
        weatherHttpApiAgent.setRequestHeaders(headers);

        weatherHttpApiAgent = assetStorageService.merge(weatherHttpApiAgent);
        weatherHttpApiAgentId = weatherHttpApiAgent.getId();

        WeatherAsset weather = new WeatherAsset("Weather");
        weather.setParent(energyManagement);
        weather.setId(UniqueIdentifierGenerator.generateId(weather.getName()));

        HttpClientAgent.HttpClientAgentLink agentLink = new HttpClientAgent.HttpClientAgentLink(weatherHttpApiAgentId);
        agentLink.setPath("weather");
        agentLink.setPollingMillis((int)halfHourInMillis);

        weather.getAttributes().addOrReplace(
                new Attribute<>("currentWeather", JSON_OBJECT)
                        .addMeta(
                                new MetaItem<>(AGENT_LINK, agentLink),
                                new MetaItem<>(LABEL, "Open Weather Map API weather end point"),
                                new MetaItem<>(READ_ONLY, true),
                                new MetaItem<>(ATTRIBUTE_LINKS, new AttributeLink[] {
                                    createWeatherApiAttributeLink(weather.getId(), "main", "temp", "temperature"),
                                    createWeatherApiAttributeLink(weather.getId(), "main", "humidity", "humidity"),
                                    createWeatherApiAttributeLink(weather.getId(), "wind", "speed", "windSpeed"),
                                    createWeatherApiAttributeLink(weather.getId(), "wind", "deg", "windDirection")
                                })
                        ));
        new Attribute<>(Asset.LOCATION, new GeoJSONPoint(4.463250, 51.918849));
        weather = assetStorageService.merge(weather);

        // ################################ Realm smartcity - Environment monitor ###################################

        Asset<?> environmentMonitor = new ThingAsset("Environment monitor");
        environmentMonitor.setRealm(this.realmCityTenant);
        environmentMonitor.setId(UniqueIdentifierGenerator.generateId(environmentMonitor.getName()));
        environmentMonitor = assetStorageService.merge(environmentMonitor);

        EnvironmentSensorAsset environment1Asset = createDemoEnvironmentAsset("Oudehaven", environmentMonitor, new GeoJSONPoint(4.49313, 51.91885), () ->
            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId));
        EnvironmentSensorAsset environment2Asset = createDemoEnvironmentAsset("Kaappark", environmentMonitor, new GeoJSONPoint(4.480434, 51.899287), () ->
            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId));
        EnvironmentSensorAsset environment3Asset = createDemoEnvironmentAsset("Museumpark", environmentMonitor, new GeoJSONPoint(4.472457, 51.912047), () ->
            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId));
        EnvironmentSensorAsset environment4Asset = createDemoEnvironmentAsset("Eendrachtsplein", environmentMonitor, new GeoJSONPoint(4.473599, 51.916292), () ->
            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId));

        EnvironmentSensorAsset[] environmentArray = {environment1Asset, environment2Asset, environment3Asset, environment4Asset};
        for (EnvironmentSensorAsset asset : environmentArray) {
            asset.setManufacturer("Intemo");
            asset.setModel("Josene outdoor");
            asset.getAttribute(EnvironmentSensorAsset.OZONE).ifPresent(assetAttribute -> {
                assetAttribute.addMeta(
                        new MetaItem<>(
                                AGENT_LINK,
                                new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                    new SimulatorReplayDatapoint[] {
                                        new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), getRandomNumberInRange(80,90)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), getRandomNumberInRange(80,90)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), getRandomNumberInRange(80,90)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), getRandomNumberInRange(80,90)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), getRandomNumberInRange(80,90)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), getRandomNumberInRange(80,90)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), getRandomNumberInRange(80,90)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), getRandomNumberInRange(80,90)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), getRandomNumberInRange(80,90)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), getRandomNumberInRange(80,90)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), getRandomNumberInRange(90,110)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), getRandomNumberInRange(90,110)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), getRandomNumberInRange(90,110)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), getRandomNumberInRange(90,110)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), getRandomNumberInRange(110,120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), getRandomNumberInRange(115,125)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), getRandomNumberInRange(115,125)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), getRandomNumberInRange(110,120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), getRandomNumberInRange(90,110)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), getRandomNumberInRange(90,110)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), getRandomNumberInRange(80,90)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), getRandomNumberInRange(80,90)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), getRandomNumberInRange(80,90)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), getRandomNumberInRange(80,90))
                                    }
                                )                            
                        )
                );
            });
            asset.setId(UniqueIdentifierGenerator.generateId(asset.getName()));
            asset = assetStorageService.merge(asset);
        }

        GroundwaterSensorAsset groundwater1Asset = createDemoGroundwaterAsset("Leuvehaven", environmentMonitor, new GeoJSONPoint(4.48413, 51.91431));
        GroundwaterSensorAsset groundwater2Asset = createDemoGroundwaterAsset("Steiger", environmentMonitor, new GeoJSONPoint(4.482887, 51.920082));
        GroundwaterSensorAsset groundwater3Asset = createDemoGroundwaterAsset("Stadhuis", environmentMonitor, new GeoJSONPoint(4.480876, 51.923212));

        GroundwaterSensorAsset[] groundwaterArray = {groundwater1Asset, groundwater2Asset, groundwater3Asset};
        for (GroundwaterSensorAsset asset : groundwaterArray) {
            asset.setManufacturer("Eijkelkamp");
            asset.setModel("TeleControlNet");
            asset.getAttribute(GroundwaterSensorAsset.SOIL_TEMPERATURE).ifPresent(assetAttribute -> {
                assetAttribute.addMeta(
                        new MetaItem<>(
                                AGENT_LINK,
                                new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                    new SimulatorReplayDatapoint[] {
                                        new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 12.2),
                                        new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 12.1),
                                        new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 12.0),
                                        new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 11.8),
                                        new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 11.7),
                                        new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 11.7),
                                        new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 11.9),
                                        new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 12.1),
                                        new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 12.8),
                                        new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 13.5),
                                        new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 13.9),
                                        new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 15.2),
                                        new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 15.3),
                                        new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 15.5),
                                        new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 15.5),
                                        new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 15.4),
                                        new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 15.2),
                                        new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 15.2),
                                        new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 14.6),
                                        new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 14.2),
                                        new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 13.8),
                                        new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 13.4),
                                        new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 12.8),
                                        new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 12.3)
                                    }
                                )
                        )
                );
            });
            asset.getAttribute(GroundwaterSensorAsset.WATER_LEVEL).ifPresent(assetAttribute -> {
                assetAttribute.addMeta(
                        new MetaItem<>(
                                AGENT_LINK,
                                new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                    new SimulatorReplayDatapoint[]{
                                        new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), getRandomNumberInRange(100, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), getRandomNumberInRange(100, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), getRandomNumberInRange(90, 110)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), getRandomNumberInRange(100, 110)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), getRandomNumberInRange(100, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120)),
                                        new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), getRandomNumberInRange(110, 120))
                                    }
                                )
                        )
                );
            });
            asset.setId(UniqueIdentifierGenerator.generateId(asset.getName()));
            asset = assetStorageService.merge(asset);
        }

        // ################################ Realm smartcity - Mobility and Safety ###################################

        Asset<?> mobilityAndSafety = new ThingAsset("Mobility and safety");
        mobilityAndSafety.setRealm(this.realmCityTenant);
        mobilityAndSafety.setId(UniqueIdentifierGenerator.generateId(mobilityAndSafety.getName()));
        mobilityAndSafety = assetStorageService.merge(mobilityAndSafety);

        // ### Parking ###

        GroupAsset parkingGroupAsset = new GroupAsset("Parking group", ParkingAsset.class);
        parkingGroupAsset.setParent(mobilityAndSafety);
        parkingGroupAsset.getAttributes().addOrReplace(
                new Attribute<>("totalOccupancy", POSITIVE_INTEGER)
                        .addMeta(
                                new MetaItem<>(LABEL, "Percentage of total parking spaces in use"),
                                new MetaItem<>(UNITS, Constants.units(Constants.UNITS_PERCENTAGE)),
                                new MetaItem<>(CONSTRAINTS, ValueConstraint.constraints(new ValueConstraint.Min(0), new ValueConstraint.Max(100))),
                                new MetaItem<>(READ_ONLY)));
        parkingGroupAsset.setId(UniqueIdentifierGenerator.generateId(parkingGroupAsset.getName()));
        parkingGroupAsset = assetStorageService.merge(parkingGroupAsset);

        ParkingAsset parking1Asset = createDemoParkingAsset("Markthal", parkingGroupAsset, new GeoJSONPoint(4.48527, 51.91984))
            .setManufacturer("SKIDATA")
            .setModel("Barrier.Gate");
        parking1Asset.getAttribute(ParkingAsset.SPACES_OCCUPIED).ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem<>(
                            AGENT_LINK,
                            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                new SimulatorReplayDatapoint[]{
                                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 34),
                                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 37),
                                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 31),
                                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 36),
                                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 32),
                                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 39),
                                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 47),
                                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 53),
                                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 165),
                                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 301),
                                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 417),
                                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 442),
                                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 489),
                                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 467),
                                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 490),
                                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 438),
                                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 457),
                                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 402),
                                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 379),
                                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 336),
                                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 257),
                                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 204),
                                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 112),
                                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 75)
                                }
                            )
                    )
            );
        });
        parking1Asset.setPriceHourly(3.75);
        parking1Asset.setPriceDaily(25.00);
        parking1Asset.setSpacesTotal(512);
        parking1Asset.setId(UniqueIdentifierGenerator.generateId(parking1Asset.getName()));
        parking1Asset = assetStorageService.merge(parking1Asset);

        ParkingAsset parking2Asset = createDemoParkingAsset("Lijnbaan", parkingGroupAsset, new GeoJSONPoint(4.47681, 51.91849));
        parking2Asset.setManufacturer("SKIDATA");
        parking2Asset.setModel("Barrier.Gate");
        parking2Asset.getAttribute(ParkingAsset.SPACES_OCCUPIED).ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem<>(
                            AGENT_LINK,
                            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                new SimulatorReplayDatapoint[]{
                                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 31),
                                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 24),
                                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 36),
                                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 38),
                                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 46),
                                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 48),
                                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 52),
                                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 89),
                                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 142),
                                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 187),
                                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 246),
                                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 231),
                                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 367),
                                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 345),
                                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 386),
                                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 312),
                                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 363),
                                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 276),
                                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 249),
                                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 256),
                                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 123),
                                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 153),
                                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 83),
                                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 25)
                                }
                            )
                    )
            );
        });
        parking2Asset.setPriceHourly(3.50);
        parking2Asset.setPriceDaily(23.00);
        parking2Asset.setSpacesTotal(390);
        parking2Asset.setId(UniqueIdentifierGenerator.generateId(parking2Asset.getName()));
        parking2Asset = assetStorageService.merge(parking2Asset);

        ParkingAsset parking3Asset = createDemoParkingAsset("Erasmusbrug", parkingGroupAsset, new GeoJSONPoint(4.48207, 51.91127));
        parking3Asset.setManufacturer("Kiestra");
        parking3Asset.setModel("Genius Rainbow");
        parking3Asset.getAttribute(ParkingAsset.SPACES_OCCUPIED).ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem<>(
                            AGENT_LINK,
                            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                new SimulatorReplayDatapoint[]{
                                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 25),
                                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 23),
                                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 23),
                                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 21),
                                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 18),
                                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 13),
                                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 29),
                                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 36),
                                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 119),
                                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 257),
                                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 357),
                                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 368),
                                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 362),
                                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 349),
                                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 370),
                                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 367),
                                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 355),
                                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 314),
                                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 254),
                                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 215),
                                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 165),
                                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 149),
                                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 108),
                                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 47)
                                }
                            )
                    )
            );
        });
        parking3Asset.setPriceHourly(3.40);
        parking3Asset.setPriceDaily(20.00);
        parking3Asset.setSpacesTotal(373);
        parking3Asset.setId(UniqueIdentifierGenerator.generateId(parking3Asset.getName()));
        parking3Asset = assetStorageService.merge(parking3Asset);

        // ### Crowd control ###

        ThingAsset assetAreaStation = new ThingAsset("Stationsplein");
        assetAreaStation.setParent(mobilityAndSafety)
                .getAttributes().addOrReplace(
                        new Attribute<>(Asset.LOCATION, STATIONSPLEIN_LOCATION),
                        new Attribute<>(BuildingAsset.POSTAL_CODE, "3013 AK"),
                        new Attribute<>(BuildingAsset.CITY, "Rotterdam"),
                        new Attribute<>(BuildingAsset.COUNTRY, "Netherlands")
                );
        assetAreaStation.setId(UniqueIdentifierGenerator.generateId(assetAreaStation.getName()));
        assetAreaStation = assetStorageService.merge(assetAreaStation);
        area1Id = assetAreaStation.getId();

        PeopleCounterAsset peopleCounter1Asset = createDemoPeopleCounterAsset("People Counter South", assetAreaStation, new GeoJSONPoint(4.470147, 51.923171), () ->
            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId));
        peopleCounter1Asset.setManufacturer("ViNotion");
        peopleCounter1Asset.setModel("ViSense");
        peopleCounter1Asset.getAttribute(PeopleCounterAsset.COUNT_GROWTH_PER_MINUTE).ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem<>(
                            AGENT_LINK,
                            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                new SimulatorReplayDatapoint[]{
                                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 0.2),
                                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 0.3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 0.1),
                                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 0.0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 0.2),
                                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 0.4),
                                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 0.5),
                                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 0.7),
                                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 1.8),
                                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 2.1),
                                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 2.4),
                                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 1.9),
                                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 1.8),
                                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 2.1),
                                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 1.8),
                                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 1.7),
                                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 2.3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 3.1),
                                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 2.8),
                                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 2.2),
                                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 1.6),
                                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 1.7),
                                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 1.1),
                                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 0.8)
                                }
                            )
                    )
            );
        });
        peopleCounter1Asset.setId(UniqueIdentifierGenerator.generateId(peopleCounter1Asset.getName()));
        peopleCounter1Asset = assetStorageService.merge(peopleCounter1Asset);

        Asset<?> peopleCounter2Asset = createDemoPeopleCounterAsset("People Counter North", assetAreaStation, new GeoJSONPoint(4.469329, 51.923700), () ->
            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId));
        peopleCounter2Asset.setManufacturer("Axis");
        peopleCounter2Asset.setModel("P1375-E");
        peopleCounter2Asset.getAttribute(PeopleCounterAsset.COUNT_GROWTH_PER_MINUTE).ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem<>(
                            AGENT_LINK,
                            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                new SimulatorReplayDatapoint[]{
                                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), 0.3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), 0.2),
                                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), 0.3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), 0.1),
                                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), 0.0),
                                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), 0.3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), 0.7),
                                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), 0.6),
                                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), 1.9),
                                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), 2.2),
                                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), 2.8),
                                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), 1.6),
                                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), 1.9),
                                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), 2.2),
                                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), 1.9),
                                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), 1.6),
                                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), 2.4),
                                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), 3.2),
                                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), 2.9),
                                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), 2.3),
                                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), 1.7),
                                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), 1.4),
                                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), 1.2),
                                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), 0.7)
                                }
                            )
                    )
            );
        });
        peopleCounter2Asset.setId(UniqueIdentifierGenerator.generateId(peopleCounter2Asset.getName()));
        peopleCounter2Asset = assetStorageService.merge(peopleCounter2Asset);

        MicrophoneAsset microphone1Asset = createDemoMicrophoneAsset("Microphone South", assetAreaStation, new GeoJSONPoint(4.470362, 51.923201), () ->
            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                new SimulatorReplayDatapoint[] {
                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), getRandomNumberInRange(50,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), getRandomNumberInRange(45,50)),
                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), getRandomNumberInRange(45,50)),
                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), getRandomNumberInRange(45,50)),
                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), getRandomNumberInRange(45,50)),
                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), getRandomNumberInRange(45,50)),
                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), getRandomNumberInRange(50,55)),
                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), getRandomNumberInRange(50,55)),
                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), getRandomNumberInRange(50,55)),
                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), getRandomNumberInRange(55,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), getRandomNumberInRange(55,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), getRandomNumberInRange(55,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), getRandomNumberInRange(60,65)),
                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), getRandomNumberInRange(60,65)),
                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), getRandomNumberInRange(55,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), getRandomNumberInRange(55,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), getRandomNumberInRange(60,65)),
                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), getRandomNumberInRange(60,70)),
                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), getRandomNumberInRange(60,65)),
                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), getRandomNumberInRange(55,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), getRandomNumberInRange(55,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), getRandomNumberInRange(60,70)),
                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), getRandomNumberInRange(60,65)),
                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), getRandomNumberInRange(50,60))
                }
            ));
        microphone1Asset.setManufacturer("Sorama");
        microphone1Asset.setModel("CAM1K");
        microphone1Asset.setId(UniqueIdentifierGenerator.generateId(microphone1Asset.getName()));
        microphone1Asset = assetStorageService.merge(microphone1Asset);

        MicrophoneAsset microphone2Asset = createDemoMicrophoneAsset("Microphone North", assetAreaStation, new GeoJSONPoint(4.469190, 51.923786), () -> 
            new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                new SimulatorReplayDatapoint[] {
                    new SimulatorReplayDatapoint(midnight.get(SECOND_OF_DAY), getRandomNumberInRange(50,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(1).get(SECOND_OF_DAY), getRandomNumberInRange(45,50)),
                    new SimulatorReplayDatapoint(midnight.plusHours(2).get(SECOND_OF_DAY), getRandomNumberInRange(45,50)),
                    new SimulatorReplayDatapoint(midnight.plusHours(3).get(SECOND_OF_DAY), getRandomNumberInRange(45,50)),
                    new SimulatorReplayDatapoint(midnight.plusHours(4).get(SECOND_OF_DAY), getRandomNumberInRange(45,50)),
                    new SimulatorReplayDatapoint(midnight.plusHours(5).get(SECOND_OF_DAY), getRandomNumberInRange(45,50)),
                    new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), getRandomNumberInRange(50,55)),
                    new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), getRandomNumberInRange(50,55)),
                    new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), getRandomNumberInRange(50,55)),
                    new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), getRandomNumberInRange(55,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), getRandomNumberInRange(55,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), getRandomNumberInRange(55,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), getRandomNumberInRange(60,65)),
                    new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), getRandomNumberInRange(60,65)),
                    new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), getRandomNumberInRange(55,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), getRandomNumberInRange(55,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), getRandomNumberInRange(60,65)),
                    new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), getRandomNumberInRange(60,70)),
                    new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), getRandomNumberInRange(60,65)),
                    new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), getRandomNumberInRange(55,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), getRandomNumberInRange(55,60)),
                    new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), getRandomNumberInRange(60,70)),
                    new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), getRandomNumberInRange(60,65)),
                    new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), getRandomNumberInRange(50,60))
                }
            ));
        microphone2Asset.setManufacturer("Sorama");
        microphone2Asset.setModel("CAM1K");
        microphone2Asset.setId(UniqueIdentifierGenerator.generateId(microphone2Asset.getName()));
        microphone2Asset = assetStorageService.merge(microphone2Asset);

        LightAsset lightStation1Asset = createDemoLightAsset("Station Light NW", assetAreaStation, new GeoJSONPoint(4.468874, 51.923881));
        lightStation1Asset.setManufacturer("Philips");
        lightStation1Asset.setModel("CityTouch");
        lightStation1Asset.setId(UniqueIdentifierGenerator.generateId(lightStation1Asset.getName()));
        lightStation1Asset = assetStorageService.merge(lightStation1Asset);

        LightAsset lightStation2Asset = createDemoLightAsset("Station Light NE", assetAreaStation, new GeoJSONPoint(4.470539, 51.923991));
        lightStation2Asset.setManufacturer("Philips");
        lightStation2Asset.setModel("CityTouch");
        lightStation2Asset.setId(UniqueIdentifierGenerator.generateId(lightStation2Asset.getName()));
        lightStation2Asset = assetStorageService.merge(lightStation2Asset);

        LightAsset lightStation3Asset = createDemoLightAsset("Station Light S", assetAreaStation, new GeoJSONPoint(4.470558, 51.923186));
        lightStation3Asset.setManufacturer("Philips");
        lightStation3Asset.setModel("CityTouch");
        lightStation3Asset.setId(UniqueIdentifierGenerator.generateId(lightStation3Asset.getName()));
        lightStation3Asset = assetStorageService.merge(lightStation3Asset);

        // ### Lighting controller ###

        LightAsset lightingControllerOPAsset = createDemoLightControllerAsset("Lighting Noordereiland", mobilityAndSafety, new GeoJSONPoint(4.496177, 51.915060));
        lightingControllerOPAsset.setManufacturer("Pharos");
        lightingControllerOPAsset.setModel("LPC X");
        lightingControllerOPAsset.setId(UniqueIdentifierGenerator.generateId(lightingControllerOPAsset.getName()));
        lightingControllerOPAsset = assetStorageService.merge(lightingControllerOPAsset);

        LightAsset lightOP1Asset = createDemoLightAsset("OnsPark1", lightingControllerOPAsset, new GeoJSONPoint(4.49626, 51.91516));
        lightOP1Asset.setManufacturer("Schréder");
        lightOP1Asset.setModel("Axia 2");
        lightOP1Asset.setId(UniqueIdentifierGenerator.generateId(lightOP1Asset.getName()));
        lightOP1Asset = assetStorageService.merge(lightOP1Asset);

        LightAsset lightOP2Asset = createDemoLightAsset("OnsPark2", lightingControllerOPAsset, new GeoJSONPoint(4.49705, 51.91549));
        lightOP2Asset.setManufacturer("Schréder");
        lightOP2Asset.setModel("Axia 2");
        lightOP2Asset.setId(UniqueIdentifierGenerator.generateId(lightOP2Asset.getName()));
        lightOP2Asset = assetStorageService.merge(lightOP2Asset);

        LightAsset lightOP3Asset = createDemoLightAsset("OnsPark3", lightingControllerOPAsset, new GeoJSONPoint(4.49661, 51.91495));
        lightOP3Asset.setManufacturer("Schréder");
        lightOP3Asset.setModel("Axia 2");
        lightOP3Asset.setId(UniqueIdentifierGenerator.generateId(lightOP3Asset.getName()));
        lightOP3Asset = assetStorageService.merge(lightOP3Asset);

        LightAsset lightOP4Asset = createDemoLightAsset("OnsPark4", lightingControllerOPAsset, new GeoJSONPoint(4.49704, 51.91520));
        lightOP4Asset.setManufacturer("Schréder");
        lightOP4Asset.setModel("Axia 2");
        lightOP4Asset.setId(UniqueIdentifierGenerator.generateId(lightOP4Asset.getName()));
        lightOP4Asset = assetStorageService.merge(lightOP4Asset);

        LightAsset lightOP5Asset = createDemoLightAsset("OnsPark5", lightingControllerOPAsset, new GeoJSONPoint(4.49758, 51.91440));
        lightOP5Asset.setManufacturer("Schréder");
        lightOP5Asset.setModel("Axia 2");
        lightOP5Asset.setId(UniqueIdentifierGenerator.generateId(lightOP5Asset.getName()));
        lightOP5Asset = assetStorageService.merge(lightOP5Asset);

        LightAsset lightOP6Asset = createDemoLightAsset("OnsPark6", lightingControllerOPAsset, new GeoJSONPoint(4.49786, 51.91452));
        lightOP6Asset.setManufacturer("Schréder");
        lightOP6Asset.setModel("Axia 2");
        lightOP6Asset.setId(UniqueIdentifierGenerator.generateId(lightOP6Asset.getName()));
        lightOP6Asset = assetStorageService.merge(lightOP6Asset);

        // ### Ships ###

        GroupAsset shipGroupAsset = new GroupAsset("Ship group", ShipAsset.class);
        shipGroupAsset.setParent(mobilityAndSafety);
        shipGroupAsset.setId(UniqueIdentifierGenerator.generateId(shipGroupAsset.getName()));
        shipGroupAsset = assetStorageService.merge(shipGroupAsset);

        ShipAsset ship1Asset = createDemoShipAsset("Hotel New York", shipGroupAsset, new GeoJSONPoint(4.48527, 51.91984));
        ship1Asset.setLength(12);
        ship1Asset.setShipType("Passenger");
        ship1Asset.setIMONumber(9183527);
        ship1Asset.setMSSINumber(244650537);
        ship1Asset.getAttribute(Asset.LOCATION).ifPresent(assetAttribute -> {
                    assetAttribute.addMeta(
                            new MetaItem<>(
                                    AGENT_LINK,
                                    new SimulatorAgent.SimulatorAgentLink(smartcitySimulatorAgentId).setSimulatorReplayData(
                                        new SimulatorReplayDatapoint[]{
                                            new SimulatorReplayDatapoint(midnight.plusHours(6).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(6).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(6).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(6).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(6).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(6).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(6).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(6).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(6).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(6).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(6).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(6).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(7).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(7).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(7).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(7).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(7).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(7).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(7).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(7).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(7).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(7).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(7).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(7).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(8).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(8).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(8).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(8).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(8).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(8).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(8).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(8).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(8).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(8).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(8).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(8).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(9).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(9).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(9).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(9).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(9).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(9).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(9).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(9).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(9).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(9).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(9).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(9).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(10).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(10).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(10).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(10).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(10).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(10).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(10).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(10).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(10).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(10).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(10).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(11).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(11).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(11).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(11).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(11).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(11).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(11).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(11).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(11).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(11).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(11).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(11).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(12).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(12).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(12).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(12).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(12).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(12).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(12).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(12).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(12).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(12).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(12).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(12).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(13).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(13).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(13).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(13).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(13).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(13).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(13).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(13).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(13).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(13).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(13).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(13).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(14).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(14).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(14).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(14).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(14).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(14).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(14).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(14).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(14).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(14).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(14).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(14).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(15).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(15).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(15).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(15).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(15).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(15).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(15).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(15).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(15).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(15).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(15).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(16).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(16).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(16).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(16).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(16).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(16).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(16).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(16).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(16).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(16).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(16).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(16).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(17).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(17).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(17).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(17).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(17).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(17).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(17).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(17).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(17).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(17).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(17).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(17).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(18).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(18).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(18).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(18).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(18).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(18).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(18).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(18).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(18).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(18).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(18).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(18).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(19).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(19).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(19).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(19).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(19).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(19).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(19).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(19).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(19).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(19).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(19).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(19).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(20).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(20).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(20).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(20).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(20).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(20).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(20).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(20).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(20).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(20).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(20).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(21).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(21).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(21).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(21).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(21).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(21).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(21).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(21).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(21).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(21).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(21).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(21).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(22).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(22).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(22).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(22).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(22).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(22).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(22).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(22).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(22).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(22).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(22).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(22).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(23).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(23).plusMinutes(5).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(23).plusMinutes(10).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(23).plusMinutes(15).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(23).plusMinutes(20).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(23).plusMinutes(25).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(23).plusMinutes(30).get(SECOND_OF_DAY), new GeoJSONPoint(4.484374, 51.903518)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(23).plusMinutes(35).get(SECOND_OF_DAY), new GeoJSONPoint(4.479779, 51.904404)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(23).plusMinutes(40).get(SECOND_OF_DAY), new GeoJSONPoint(4.482914, 51.906769)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(23).plusMinutes(45).get(SECOND_OF_DAY), new GeoJSONPoint(4.486156, 51.908570)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(23).plusMinutes(50).get(SECOND_OF_DAY), new GeoJSONPoint(4.483362, 51.911897)),
                                            new SimulatorReplayDatapoint(midnight.plusHours(23).plusMinutes(55).get(SECOND_OF_DAY), new GeoJSONPoint(4.482669, 51.916436))
                                        }
                                    )
                            )
                    );
                });
        ship1Asset.setId(UniqueIdentifierGenerator.generateId(ship1Asset.getName()));
        ship1Asset = assetStorageService.merge(ship1Asset);

        // ### Mobile app config ###

        persistenceService.doTransaction(entityManager ->
            entityManager.merge(new ConsoleAppConfig(
                realmCityTenant,
                "https://demo.openremote.io/mobile/?realm=smartcity&consoleProviders=geofence push storage",
                "https://demo.openremote.io/main/?realm=smartcity&consoleProviders=geofence push storage&consoleAutoEnable=true#!geofences",
                false,
                ConsoleAppConfig.MenuPosition.BOTTOM_LEFT,
                null,
                "#4D9D2A",
                "#AFAFAF",
                new ConsoleAppConfig.AppLink[]{
                    new ConsoleAppConfig.AppLink("Map", "https://demo.openremote.io/mobile/?realm=smartcity&consoleProviders=geofence push storage&consoleAutoEnable=true#!geofences")
                }))
        );
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    protected static AttributeLink createWeatherApiAttributeLink(String assetId, String jsonParentName, String jsonName, String parameter) {
        return new AttributeLink(
                new AttributeRef(assetId, parameter),
                null,
                new ValueFilter[]{
                        new JsonPathFilter("$." + jsonParentName + "." + jsonName, true, false),
                }
        );
    }
}
