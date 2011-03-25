package org.intermine.api.tracker.track;

/*
 * Copyright (C) 2002-2011 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */
import org.intermine.api.tracker.util.TrackerUtil;

public class QueryTrack extends TrackAbstract {
    private String type;

    public QueryTrack(String type, long timestamp) {
        this.type = type;
        this.timestamp = timestamp;
    }

    @Override
    public Object[] getFormattedTrack() {
        return new Object[] {type, timestamp};
    }

    @Override
    public String getTableName() {
        return TrackerUtil.QUERY_TRACKER_TABLE;
    }

    @Override
    public boolean validate() {
        if (type != null && !"".equals(type)) {
            return true;
        }
        return false;
    }

}
