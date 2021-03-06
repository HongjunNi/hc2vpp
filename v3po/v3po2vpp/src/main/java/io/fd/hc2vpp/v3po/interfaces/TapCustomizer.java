/*
 * Copyright (c) 2016 Cisco and/or its affiliates.
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

package io.fd.hc2vpp.v3po.interfaces;

import io.fd.hc2vpp.common.translate.util.AbstractInterfaceTypeCustomizer;
import io.fd.hc2vpp.common.translate.util.JvppReplyConsumer;
import io.fd.hc2vpp.common.translate.util.MacTranslator;
import io.fd.hc2vpp.common.translate.util.NamingContext;
import io.fd.honeycomb.translate.write.WriteContext;
import io.fd.honeycomb.translate.write.WriteFailedException;
import io.fd.vpp.jvpp.core.dto.TapConnect;
import io.fd.vpp.jvpp.core.dto.TapConnectReply;
import io.fd.vpp.jvpp.core.dto.TapDelete;
import io.fd.vpp.jvpp.core.dto.TapDeleteReply;
import io.fd.vpp.jvpp.core.dto.TapModify;
import io.fd.vpp.jvpp.core.dto.TapModifyReply;
import io.fd.vpp.jvpp.core.future.FutureJVppCore;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev170607.interfaces._interface.Tap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TapCustomizer extends AbstractInterfaceTypeCustomizer<Tap> implements MacTranslator, JvppReplyConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(TapCustomizer.class);
    private final NamingContext interfaceContext;

    public TapCustomizer(final FutureJVppCore vppApi, final NamingContext interfaceContext) {
        super(vppApi);
        this.interfaceContext = interfaceContext;
    }

    @Override
    protected Class<? extends InterfaceType> getExpectedInterfaceType() {
        return org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev170607.Tap.class;
    }

    @Override
    protected final void writeInterface(@Nonnull final InstanceIdentifier<Tap> id, @Nonnull final Tap dataAfter,
                                        @Nonnull final WriteContext writeContext)
            throws WriteFailedException {
        final String ifcName = id.firstKeyOf(Interface.class).getName();
        createTap(id, ifcName, dataAfter, writeContext);
    }

    @Override
    public void updateCurrentAttributes(@Nonnull final InstanceIdentifier<Tap> id, @Nonnull final Tap dataBefore,
                                        @Nonnull final Tap dataAfter, @Nonnull final WriteContext writeContext)
            throws WriteFailedException {
        final String ifcName = id.firstKeyOf(Interface.class).getName();

        final int index;
        try {
            index = interfaceContext.getIndex(ifcName, writeContext.getMappingContext());
        } catch (IllegalArgumentException e) {
            throw new WriteFailedException.UpdateFailedException(id, dataBefore, dataAfter, e);
        }

        modifyTap(id, ifcName, index, dataBefore, dataAfter);
    }

    @Override
    public void deleteCurrentAttributes(@Nonnull final InstanceIdentifier<Tap> id, @Nonnull final Tap dataBefore,
                                        @Nonnull final WriteContext writeContext)
            throws WriteFailedException {
        final String ifcName = id.firstKeyOf(Interface.class).getName();

        final int index;
        try {
            index = interfaceContext.getIndex(ifcName, writeContext.getMappingContext());
        } catch (IllegalArgumentException e) {
            throw new WriteFailedException.DeleteFailedException(id, e);
        }

        deleteTap(id, ifcName, index, dataBefore, writeContext);
    }

    private void createTap(final InstanceIdentifier<Tap> id, final String swIfName, final Tap tap,
                           final WriteContext writeContext) throws WriteFailedException {
        LOG.debug("Setting tap interface: {}. Tap: {}", swIfName, tap);
        final CompletionStage<TapConnectReply> tapConnectFuture = getFutureJVpp()
                .tapConnect(getTapConnectRequest(tap));
        final TapConnectReply reply = getReplyForCreate(tapConnectFuture.toCompletableFuture(), id, tap);
        LOG.debug("Tap set successfully for: {}, tap: {}", swIfName, tap);
        // Add new interface to our interface context
        interfaceContext.addName(reply.swIfIndex, swIfName, writeContext.getMappingContext());
    }

    private void modifyTap(final InstanceIdentifier<Tap> id, final String swIfName, final int index,
                           final Tap tapBefore, final Tap tapAfter) throws WriteFailedException {
        LOG.debug("Modifying tap interface: {}. Tap: {}", swIfName, tapAfter);
        final CompletionStage<TapModifyReply> vxlanAddDelTunnelReplyCompletionStage =
                getFutureJVpp()
                        .tapModify(getTapModifyRequest(tapAfter.getTapName(), index, tapAfter.getMac(),
                                tapAfter.getDeviceInstance()));
        getReplyForUpdate(vxlanAddDelTunnelReplyCompletionStage.toCompletableFuture(), id, tapBefore, tapAfter);
        LOG.debug("Tap modified successfully for: {}, tap: {}", swIfName, tapAfter);
    }

    private void deleteTap(final InstanceIdentifier<Tap> id, final String swIfName, final int index,
                           final Tap dataBefore, final WriteContext writeContext)
            throws WriteFailedException {
        LOG.debug("Deleting tap interface: {}. Tap: {}", swIfName, dataBefore);
        final CompletionStage<TapDeleteReply> vxlanAddDelTunnelReplyCompletionStage =
                getFutureJVpp().tapDelete(getTapDeleteRequest(index));
        getReplyForDelete(vxlanAddDelTunnelReplyCompletionStage.toCompletableFuture(), id);
        LOG.debug("Tap deleted successfully for: {}, tap: {}", swIfName, dataBefore);
        // Remove deleted interface from interface context
        interfaceContext.removeName(swIfName, writeContext.getMappingContext());
    }

    private TapConnect getTapConnectRequest(final Tap tap) {
        final TapConnect tapConnect = new TapConnect();
        tapConnect.tapName = tap.getTapName().getBytes();

        final PhysAddress mac = tap.getMac();
        if (mac == null) {
            tapConnect.useRandomMac = 1;
            tapConnect.macAddress = new byte[6];
        } else {
            tapConnect.useRandomMac = 0;
            tapConnect.macAddress = parseMac(mac.getValue());
        }

        final Long deviceInstance = tap.getDeviceInstance();
        if (deviceInstance == null) {
            tapConnect.renumber = 0;
        } else {
            tapConnect.renumber = 1;
            tapConnect.customDevInstance = Math.toIntExact(deviceInstance);
        }

        final String tag = tap.getTag();
        if (tag != null) {
            tapConnect.tag = tag.getBytes(StandardCharsets.US_ASCII);
        }

        return tapConnect;
    }

    private TapModify getTapModifyRequest(final String tapName, final int swIndex, final PhysAddress mac,
                                          final Long deviceInstance) {
        final TapModify tapConnect = new TapModify();
        tapConnect.tapName = tapName.getBytes();
        tapConnect.swIfIndex = swIndex;

        if (mac == null) {
            tapConnect.useRandomMac = 1;
            tapConnect.macAddress = new byte[6];
        } else {
            tapConnect.useRandomMac = 0;
            tapConnect.macAddress = parseMac(mac.getValue());
        }

        if (deviceInstance == null) {
            tapConnect.renumber = 0;
        } else {
            tapConnect.renumber = 1;
            tapConnect.customDevInstance = Math.toIntExact(deviceInstance);
        }

        return tapConnect;
    }

    private TapDelete getTapDeleteRequest(final int swIndex) {
        final TapDelete tapConnect = new TapDelete();
        tapConnect.swIfIndex = swIndex;
        return tapConnect;
    }
}
