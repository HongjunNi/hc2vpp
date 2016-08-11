package org.opendaylight.yang.gen.v1.urn.honeycomb.params.xml.ns.yang.v3po2vpp.rev160406;

import io.fd.honeycomb.translate.v3po.InterfacesStateReaderFactory;

public class InterfacesStateHoneycombReaderModule extends
        org.opendaylight.yang.gen.v1.urn.honeycomb.params.xml.ns.yang.v3po2vpp.rev160406.AbstractInterfacesStateHoneycombReaderModule {

    public InterfacesStateHoneycombReaderModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier,
                                                org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public InterfacesStateHoneycombReaderModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier,
                                                org.opendaylight.controller.config.api.DependencyResolver dependencyResolver,
                                                org.opendaylight.yang.gen.v1.urn.honeycomb.params.xml.ns.yang.v3po2vpp.rev160406.InterfacesStateHoneycombReaderModule oldModule,
                                                java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        return new InterfacesStateReaderFactory(getVppJvppDependency(),
                getInterfaceContextIfcStateDependency(),
                getBridgeDomainContextIfcStateDependency(),
                getClassifyTableContextDependency());
    }

}
