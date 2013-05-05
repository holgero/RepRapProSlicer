/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.reprap.configuration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.lang.reflect.Field;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ConfigurationPersistenceTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();
    private final Configuration configuration = Configuration.getInstance();
    private JAXBContext context;
    private Unmarshaller unmarshaller;
    private Marshaller marshaller;

    @Before
    public void setup() throws Exception {
        context = JAXBContext.newInstance(Configuration.class);
        marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        unmarshaller = context.createUnmarshaller();
    }

    @Test
    public void testWriteReadCycleOfCurrentConfiguration() throws Exception {
        final File xmlFile = folder.newFile("test.xml");
        marshaller.marshal(configuration, xmlFile);
        final Configuration result = (Configuration) unmarshaller.unmarshal(xmlFile);
        assertThat(result.getCurrentConfiguration(), is(notNullValue()));

        compareFieldByField(result.getCurrentConfiguration(), configuration.getCurrentConfiguration());
    }

    private static void compareFieldByField(final Object actual, final Object expected) {
        for (final Field field : expected.getClass().getDeclaredFields()) {
            assertThat(actual, containsInField(field).theSameValueAs(expected));
        }
    }

    private static final class MatcherCreator {
        private final Field field;

        public MatcherCreator(final Field field) {
            this.field = field;
            field.setAccessible(true);
        }

        public Matcher<? super Object> theSameValueAs(final Object expected) {
            return new BaseMatcher<Object>() {
                @Override
                public boolean matches(final Object actual) {
                    try {
                        if (field.getType().getCanonicalName().startsWith("org.reprap")) {
                            try {
                                compareFieldByField(field.get(actual), field.get(expected));
                            } catch (final AssertionError e) {
                                throw new AssertionError("In field " + field.getName() + ": " + e.getMessage());
                            }
                            return true;
                        } else {
                            if (expected == null || actual == null) {
                                return false;
                            }
                            if (field.get(expected) == null) {
                                return field.get(actual) == null;
                            }
                            return field.get(expected).equals(field.get(actual));
                        }
                    } catch (final IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void describeTo(final Description descr) {
                    try {
                        descr.appendText("Field " + field.getName() + " has value ").appendValue(field.get(expected));
                    } catch (final IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void describeMismatch(final Object item, final Description description) {
                    try {
                        if (item != null) {
                            description.appendText("was ").appendValue(field.get(item));
                        } else {
                            description.appendText("containing object was null.");
                        }
                    } catch (final IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        }
    }

    private static MatcherCreator containsInField(final Field field) {
        return new MatcherCreator(field);
    }
}
