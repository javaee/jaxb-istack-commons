package com.oracle.istack.maven;

import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.providers.http.LightweightHttpWagon;
import org.apache.maven.wagon.providers.http.LightweightHttpsWagon;
import org.sonatype.aether.connector.wagon.WagonProvider;

/**
 * A simplistic provider for wagon instances when no Plexus-compatible IoC container is used.
 */
public class ManualWagonProvider implements WagonProvider {

    @Override
    public Wagon lookup(String roleHint) throws Exception {
        if ("http".equals(roleHint)) {
            return new LightweightHttpWagon();
        } else if ("https".equals(roleHint)) {
            return new LightweightHttpsWagon();
        }
        return null;
    }

    @Override
    public void release(Wagon wagon) {

    }

}