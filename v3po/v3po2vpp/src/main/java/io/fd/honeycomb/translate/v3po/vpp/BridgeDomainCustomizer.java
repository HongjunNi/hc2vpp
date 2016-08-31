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

package io.fd.honeycomb.translate.v3po.vpp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.fd.honeycomb.translate.v3po.util.TranslateUtils.booleanToByte;

import com.google.common.base.Preconditions;
import io.fd.honeycomb.translate.spi.write.ListWriterCustomizer;
import io.fd.honeycomb.translate.v3po.util.FutureJVppCustomizer;
import io.fd.honeycomb.translate.v3po.util.WriteTimeoutException;
import io.fd.honeycomb.translate.write.WriteContext;
import io.fd.honeycomb.translate.v3po.util.NamingContext;
import io.fd.honeycomb.translate.v3po.util.TranslateUtils;
import io.fd.honeycomb.translate.write.WriteFailedException;
import javax.annotation.Nonnull;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.bridge.domains.BridgeDomain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.bridge.domains.BridgeDomainKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.openvpp.jvpp.VppBaseCallException;
import org.openvpp.jvpp.core.dto.BridgeDomainAddDel;
import org.openvpp.jvpp.core.dto.BridgeDomainAddDelReply;
import org.openvpp.jvpp.core.future.FutureJVppCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BridgeDomainCustomizer
    extends FutureJVppCustomizer
    implements ListWriterCustomizer<BridgeDomain, BridgeDomainKey> {

    private static final Logger LOG = LoggerFactory.getLogger(BridgeDomainCustomizer.class);

    private static final byte ADD_OR_UPDATE_BD = (byte) 1;
    private final NamingContext bdContext;

    public BridgeDomainCustomizer(@Nonnull final FutureJVppCore futureJVppCore, @Nonnull final NamingContext bdContext) {
        super(futureJVppCore);
        this.bdContext = Preconditions.checkNotNull(bdContext, "bdContext should not be null");
    }

    private BridgeDomainAddDelReply addOrUpdateBridgeDomain(@Nonnull final InstanceIdentifier<BridgeDomain> id,
                                                            final int bdId, @Nonnull final BridgeDomain bd)
        throws VppBaseCallException, WriteTimeoutException {
        final BridgeDomainAddDelReply reply;
        final BridgeDomainAddDel request = new BridgeDomainAddDel();
        request.bdId = bdId;
        request.flood = booleanToByte(bd.isFlood());
        request.forward = booleanToByte(bd.isForward());
        request.learn = booleanToByte(bd.isLearn());
        request.uuFlood = booleanToByte(bd.isUnknownUnicastFlood());
        request.arpTerm = booleanToByte(bd.isArpTermination());
        request.isAdd = ADD_OR_UPDATE_BD;

        reply = TranslateUtils.getReplyForWrite(getFutureJVpp().bridgeDomainAddDel(request).toCompletableFuture(), id);
        LOG.debug("Bridge domain {} (id={}) add/update successful", bd.getName(), bdId);
        return reply;
    }

    @Override
    public void writeCurrentAttributes(@Nonnull final InstanceIdentifier<BridgeDomain> id,
                                       @Nonnull final BridgeDomain dataBefore,
                                       @Nonnull final WriteContext ctx)
        throws WriteFailedException {
        LOG.debug("writeCurrentAttributes: id={}, current={}, ctx={}", id, dataBefore, ctx);
        final String bdName = dataBefore.getName();

        try {
            int index;
            if (bdContext.containsIndex(bdName, ctx.getMappingContext())) {
                index = bdContext.getIndex(bdName, ctx.getMappingContext());
            } else {
                // FIXME we need the bd index to be returned by VPP or we should have a counter field
                // (maybe in context similar to artificial name)
                // Here we assign the next available ID from bdContext's perspective
                index = 1;
                while (bdContext.containsName(index, ctx.getMappingContext())) {
                    index++;
                }
            }
            addOrUpdateBridgeDomain(id, index, dataBefore);
            bdContext.addName(index, bdName, ctx.getMappingContext());
        } catch (VppBaseCallException e) {
            LOG.warn("Failed to create bridge domain", e);
            throw new WriteFailedException.CreateFailedException(id, dataBefore, e);
        }
    }

    @Override
    public void deleteCurrentAttributes(@Nonnull final InstanceIdentifier<BridgeDomain> id,
                                        @Nonnull final BridgeDomain dataBefore,
                                        @Nonnull final WriteContext ctx)
        throws WriteFailedException {
        LOG.debug("deleteCurrentAttributes: id={}, dataBefore={}, ctx={}", id, dataBefore, ctx);
        final String bdName = id.firstKeyOf(BridgeDomain.class).getName();
        int bdId = bdContext.getIndex(bdName, ctx.getMappingContext());
        try {

            final BridgeDomainAddDel request = new BridgeDomainAddDel();
            request.bdId = bdId;

            TranslateUtils.getReplyForWrite(getFutureJVpp().bridgeDomainAddDel(request).toCompletableFuture(), id);
            LOG.debug("Bridge domain {} (id={}) deleted successfully", bdName, bdId);
        } catch (VppBaseCallException e) {
            LOG.warn("Bridge domain {} (id={}) delete failed", bdName, bdId);
            throw new WriteFailedException.DeleteFailedException(id, e);
        }
    }

    @Override
    public void updateCurrentAttributes(@Nonnull final InstanceIdentifier<BridgeDomain> id,
                                        @Nonnull final BridgeDomain dataBefore, @Nonnull final BridgeDomain dataAfter,
                                        @Nonnull final WriteContext ctx)
        throws WriteFailedException {
        LOG.debug("updateCurrentAttributes: id={}, dataBefore={}, dataAfter={}, ctx={}", id, dataBefore, dataAfter,
            ctx);

        final String bdName = checkNotNull(dataAfter.getName());
        checkArgument(bdName.equals(dataBefore.getName()),
            "BridgeDomain name changed. It should be deleted and then created.");

        try {
            addOrUpdateBridgeDomain(id, bdContext.getIndex(bdName, ctx.getMappingContext()), dataAfter);
        } catch (VppBaseCallException e) {
            LOG.warn("Failed to create bridge domain", e);
            throw new WriteFailedException.UpdateFailedException(id, dataBefore, dataAfter, e);
        }
    }

}