/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.cadixdev.atlas;


import java.util.List;

import org.cadixdev.bombe.asm.jar.ClassProvider;



/**
 * A {@link ClassProvider class provider} backed by many other class providers.
 *
 * @author Jamie Mansfield
 * @since 0.1.0
 */
public class CompositeClassProvider implements ClassProvider {

    private final List<org.cadixdev.bombe.jar.ClassProvider> providers;

    public CompositeClassProvider(final List<org.cadixdev.bombe.jar.ClassProvider> providers) {
        this.providers = providers;
    }

    @Override
    public byte[] get(final String klass) {
        for (final org.cadixdev.bombe.jar.ClassProvider provider : this.providers) {
            final byte[] raw = provider.get(klass);
            if (raw != null) return raw;
        }
        return null;
    }

}
