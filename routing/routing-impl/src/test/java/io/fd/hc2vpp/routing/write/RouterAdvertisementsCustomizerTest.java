/*
 * Copyright (c) 2017 Cisco and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fd.hc2vpp.routing.write;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.fd.hc2vpp.common.test.write.WriterCustomizerTest;
import io.fd.hc2vpp.common.translate.util.NamingContext;
import io.fd.hc2vpp.routing.helpers.SchemaContextTestHelper;
import io.fd.honeycomb.test.tools.HoneycombTestRunner;
import io.fd.honeycomb.test.tools.annotations.InjectTestData;
import io.fd.honeycomb.translate.write.WriteFailedException;
import io.fd.vpp.jvpp.core.dto.SwInterfaceIp6NdRaConfig;
import io.fd.vpp.jvpp.core.dto.SwInterfaceIp6NdRaConfigReply;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ipv6.unicast.routing.rev170917.Interface1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ipv6.unicast.routing.rev170917.routing.routing.instance.interfaces._interface.Ipv6RouterAdvertisements;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.routing.rev140524.Routing;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.routing.rev140524.routing.RoutingInstance;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.routing.rev140524.routing.RoutingInstanceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.routing.rev140524.routing.routing.instance.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.routing.rev140524.routing.routing.instance.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.routing.rev140524.routing.routing.instance.interfaces.InterfaceKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@RunWith(HoneycombTestRunner.class)
public class RouterAdvertisementsCustomizerTest extends WriterCustomizerTest implements SchemaContextTestHelper {

    private static final String INSTANCE_NAME = "tst-protocol";
    private static final String CTX_NAME = "interface-context";
    private static final String IFC_NAME = "eth0";
    private static final int IFC_INDEX = 1;
    private static final InstanceIdentifier<Ipv6RouterAdvertisements> IID = InstanceIdentifier.create(Routing.class)
        .child(RoutingInstance.class, new RoutingInstanceKey(INSTANCE_NAME)).child(Interfaces.class)
        .child(Interface.class, new InterfaceKey(IFC_NAME)).augmentation(
            Interface1.class).child(Ipv6RouterAdvertisements.class);

    private static final String RA_PATH = "/hc2vpp-ietf-routing:routing" +
        "/hc2vpp-ietf-routing:routing-instance[hc2vpp-ietf-routing:name='" + INSTANCE_NAME + "']" +
        "/hc2vpp-ietf-routing:interfaces";

    private RouterAdvertisementsCustomizer customizer;
    private NamingContext interfaceContext = new NamingContext("ifaces", CTX_NAME);


    @Override
    protected void setUpTest() throws Exception {
        customizer = new RouterAdvertisementsCustomizer(api, interfaceContext);
        defineMapping(mappingContext, IFC_NAME, IFC_INDEX, CTX_NAME);
        when(api.swInterfaceIp6NdRaConfig(any())).thenReturn(future(new SwInterfaceIp6NdRaConfigReply()));
    }

    @Test
    public void testWrite(@InjectTestData(resourcePath = "/ra/simpleRa.json", id = RA_PATH) Interfaces ifc)
        throws WriteFailedException {
        final Ipv6RouterAdvertisements data = getRA(ifc);
        customizer.writeCurrentAttributes(IID, data, writeContext);
        final SwInterfaceIp6NdRaConfig request = new SwInterfaceIp6NdRaConfig();
        request.swIfIndex = IFC_INDEX;
        request.maxInterval = 600;
        request.managed = 1;
        verify(api).swInterfaceIp6NdRaConfig(request);
    }

    @Test
    public void testUpdate(@InjectTestData(resourcePath = "/ra/complexRa.json", id = RA_PATH) Interfaces ifc)
        throws WriteFailedException {
        final Ipv6RouterAdvertisements data = getRA(ifc);
        customizer.updateCurrentAttributes(IID, mock(Ipv6RouterAdvertisements.class), data, writeContext);
        final SwInterfaceIp6NdRaConfig request = new SwInterfaceIp6NdRaConfig();
        request.swIfIndex = IFC_INDEX;
        request.initialCount = 2;
        request.initialInterval = 15;
        request.maxInterval = 100;
        request.minInterval = 20;
        request.lifetime = 601;
        request.defaultRouter = 1;
        verify(api).swInterfaceIp6NdRaConfig(request);
    }

    @Test
    public void testDelete() throws WriteFailedException {
        customizer.deleteCurrentAttributes(IID, mock(Ipv6RouterAdvertisements.class), writeContext);
        final SwInterfaceIp6NdRaConfig request = new SwInterfaceIp6NdRaConfig();
        request.swIfIndex = IFC_INDEX;
        request.suppress = 1;
        verify(api).swInterfaceIp6NdRaConfig(request);
    }

    private static Ipv6RouterAdvertisements getRA(final Interfaces ifc) {
        return ifc.getInterface().get(0).getAugmentation(Interface1.class).getIpv6RouterAdvertisements();
    }
}