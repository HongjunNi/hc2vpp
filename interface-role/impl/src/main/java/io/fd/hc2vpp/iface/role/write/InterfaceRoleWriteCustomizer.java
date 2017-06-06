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

package io.fd.hc2vpp.iface.role.write;


import io.fd.honeycomb.translate.spi.write.ListWriterCustomizer;
import io.fd.honeycomb.translate.write.WriteContext;
import io.fd.honeycomb.translate.write.WriteFailedException;
import javax.annotation.Nonnull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang._interface.role.rev170615._interface.role.grouping.roles.Role;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang._interface.role.rev170615._interface.role.grouping.roles.RoleKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterfaceRoleWriteCustomizer implements ListWriterCustomizer<Role, RoleKey> {

    private static final Logger LOG = LoggerFactory.getLogger(InterfaceRoleWriteCustomizer.class);

    @Override
    public void writeCurrentAttributes(@Nonnull final InstanceIdentifier<Role> instanceIdentifier,
                                       @Nonnull final Role role,
                                       @Nonnull final WriteContext writeContext) throws WriteFailedException {
        LOG.debug("Writing interface role {} for interface {}", role,
                instanceIdentifier.firstKeyOf(Interface.class).getName());
    }

    @Override
    public void updateCurrentAttributes(@Nonnull final InstanceIdentifier<Role> instanceIdentifier,
                                        @Nonnull final Role roleBefore,
                                        @Nonnull final Role roleAfter,
                                        @Nonnull final WriteContext writeContext)
            throws WriteFailedException {
        LOG.debug("Updating interface role from {} to {} for interface {}", roleBefore, roleAfter,
                instanceIdentifier.firstKeyOf(Interface.class).getName());
    }

    @Override
    public void deleteCurrentAttributes(@Nonnull final InstanceIdentifier<Role> instanceIdentifier,
                                        @Nonnull final Role role,
                                        @Nonnull final WriteContext writeContext) throws WriteFailedException {
        LOG.debug("Removing interface role {} for interface {}", role,
                instanceIdentifier.firstKeyOf(Interface.class).getName());
    }
}