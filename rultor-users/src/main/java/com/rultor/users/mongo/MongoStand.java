/**
 * Copyright (c) 2009-2013, rultor.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the rultor.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.rultor.users.mongo;

import com.jcabi.aspects.Cacheable;
import com.jcabi.aspects.Immutable;
import com.jcabi.aspects.Loggable;
import com.jcabi.aspects.Tv;
import com.jcabi.log.Logger;
import com.jcabi.urn.URN;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.WriteResult;
import com.rexsl.test.SimpleXml;
import com.rexsl.test.XmlDocument;
import com.rultor.snapshot.Snapshot;
import com.rultor.spi.Pageable;
import com.rultor.spi.Pulse;
import com.rultor.spi.Spec;
import com.rultor.spi.Stand;
import com.rultor.tools.Time;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import javax.xml.transform.dom.DOMSource;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.w3c.dom.Document;
import org.xembly.ImpossibleModificationException;
import org.xembly.XemblySyntaxException;

/**
 * Stand in Mongo.
 *
 * <pre>
 * pulses {
 *   pulse: String,
 *   stand: String,
 *   updated: Time,
 *   xembly: String,
 *   tags: String[]
 * };
 * </pre>
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 1.0
 * @checkstyle ClassDataAbstractionCoupling (500 lines)
 */
@Immutable
@ToString
@EqualsAndHashCode(of = { "mongo", "origin" })
@Loggable(Loggable.DEBUG)
@SuppressWarnings({ "PMD.TooManyMethods", "PMD.ExcessiveImports" })
final class MongoStand implements Stand {

    /**
     * MongoDB table name.
     */
    public static final String TABLE = "stands";

    /**
     * MongoDB table column.
     */
    public static final String ATTR_STAND = "stand";

    /**
     * MongoDB table column.
     */
    public static final String ATTR_PULSE = "pulse";

    /**
     * MongoDB table column.
     */
    public static final String ATTR_UPDATED = "updated";

    /**
     * MongoDB table column.
     */
    public static final String ATTR_XEMBLY = "xembly";

    /**
     * MongoDB table column.
     */
    public static final String ATTR_TAGS = "tags";

    /**
     * Mongo container.
     */
    private final transient Mongo mongo;

    /**
     * Original stand.
     */
    private final transient Stand origin;

    /**
     * Public ctor.
     * @param mng Mongo container
     * @param stand Original
     */
    protected MongoStand(final Mongo mng, final Stand stand) {
        this.mongo = mng;
        this.origin = stand;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void post(final String pulse, final String xembly) {
        final String script = this.append(pulse, xembly);
        final Document dom = Snapshot.empty();
        try {
            new Snapshot(script).apply(dom);
            this.post(pulse, dom, script);
        } catch (XemblySyntaxException ex) {
            Logger.warn(
                this, "invalid syntax in `%s`: %s",
                xembly, ExceptionUtils.getRootCauseMessage(ex)
            );
        } catch (ImpossibleModificationException ex) {
            Logger.warn(
                this, "impossibe to modify `%s`: %s",
                xembly, ExceptionUtils.getRootCauseMessage(ex)
            );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Pageable<Pulse> pulses() {
        return new Pageable<Pulse>() {
            @Override
            public Iterator<Pulse> iterator() {
                return MongoStand.this.iterator();
            }
            @Override
            public Pageable<Pulse> tail(final Pulse head) throws IOException {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return this.origin.name();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URN owner() {
        return this.origin.owner();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void acl(final Spec spec) {
        this.origin.acl(spec);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Spec acl() {
        return this.origin.acl();
    }

    /**
     * Save data.
     * @param pulse Pulse name
     * @param dom DOM
     * @param xembly Xembly to post
     */
    private void post(final String pulse, final Document dom,
        final String xembly) {
        final XmlDocument xml = new SimpleXml(new DOMSource(dom));
        final WriteResult result = this.collection().update(
            new BasicDBObject()
                .append(MongoStand.ATTR_PULSE, pulse)
                .append(MongoStand.ATTR_STAND, this.name()),
            new BasicDBObject()
                .append(MongoStand.ATTR_PULSE, pulse)
                .append(MongoStand.ATTR_STAND, this.name())
                .append(MongoStand.ATTR_UPDATED, new Time().toString())
                .append(MongoStand.ATTR_XEMBLY, xembly)
                .append(MongoStand.ATTR_TAGS, this.tags(xml)),
            true, false
        );
        Validate.isTrue(
            result.getLastError().ok(),
            "failed to create new pulse `%s`: %s",
            pulse, result.getLastError().getErrorMessage()
        );
    }

    /**
     * Load previous script of snapshot and append this suffix to it.
     * @param pulse Pulse to read
     * @param xembly Suffix to append
     * @return New script
     */
    private String append(final String pulse, final String xembly) {
        final DBCursor cursor = this.collection().find(
            new BasicDBObject()
                .append(MongoStand.ATTR_PULSE, pulse)
                .append(MongoStand.ATTR_STAND, this.name())
        );
        try {
            final StringBuilder script = new StringBuilder();
            if (cursor.hasNext()) {
                script.append(cursor.next().get(MongoStand.ATTR_XEMBLY));
            }
            return script.append(xembly).toString();
        } finally {
            cursor.close();
        }
    }

    /**
     * Fetch all visible tags.
     * @param xml XML to fetch from
     * @return Array of tags
     */
    private Collection<String> tags(final XmlDocument xml) {
        return xml.xpath("//tags/tag/label/text()");
    }

    /**
     * Iterator of pulses.
     * @return Iterator
     */
    private Iterator<Pulse> iterator() {
        final DBCursor cursor = this.collection().find(
            new BasicDBObject(MongoStand.ATTR_STAND, this.name())
        );
        new Timer().schedule(
            new TimerTask() {
                @Override
                public void run() {
                    cursor.close();
                }
            },
            TimeUnit.SECONDS.toMillis(Tv.TEN)
        );
        cursor.sort(new BasicDBObject(MongoStand.ATTR_UPDATED, -1));
        // @checkstyle AnonInnerLength (50 lines)
        return new Iterator<Pulse>() {
            @Override
            public boolean hasNext() {
                return cursor.hasNext();
            }
            @Override
            public Pulse next() {
                return new Pulse() {
                    @Override
                    public String xembly() throws IOException {
                        return cursor.next().get(MongoStand.ATTR_XEMBLY)
                            .toString();
                    }
                    @Override
                    public InputStream stream() throws IOException {
                        throw new UnsupportedOperationException();
                    }
                };
            }
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Collection.
     * @return Mongo collection
     */
    @Cacheable(forever = true)
    private DBCollection collection() {
        try {
            return this.mongo.get().getCollection(MongoStand.TABLE);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
