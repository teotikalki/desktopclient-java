/*
 *  Kontalk Java client
 *  Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.model;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.Database;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class User {
    private final static Logger LOGGER = Logger.getLogger(User.class.getName());

    public static enum Available {UNKNOWN, YES, NO};

    public final static String TABLE = "user";
    public final static String CREATE_TABLE = "(" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "jid TEXT NOT NULL UNIQUE, " +
            "name TEXT, " +
            "status TEXT, " +
            "last_seen INTEGER " +
            //"public_KEY TEXT UNIQUE, " +
            //"key_fingerprint TEXT UNIQUE" +
            ")";

    private final int mID;
    private String mJID;
    private String mName;
    private String mStatus = null;
    private Date mLastSeen = null;
    private Available mAvailable = Available.UNKNOWN;
    //private ItemType mType;

    /**
     * Used for incoming messages of unknown user.
     */
    User(String jid) {
        this(jid, null);
    }

    /**
     * Used for creating new users (eg from roster).
     */
    User(String jid, String name) {
        mJID = StringUtils.parseBareAddress(jid);
        mName = name;

        Database db = Database.getInstance();
        List<Object> values = new LinkedList();
        values.add(mJID);
        values.add(mName);
        values.add(mStatus);
        values.add(mLastSeen);
        mID = db.execInsert(TABLE, values);
        if (mID < 1)
            LOGGER.log(Level.WARNING, "couldn't insert user");
    }

    /**
     * Used for loading users from database
     */
    User(int id, String jid, String name, String status, Date lastSeen) {
        mID = id;
        mJID = jid;
        mName = name;
        mStatus = status;
        mLastSeen = lastSeen;
    }

    public String getJID() {
        return mJID;
    }

    public void setJID(String jid) {
        mJID = StringUtils.parseBareAddress(jid);
        this.save();
    }

    public int getID() {
        return mID;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
        this.save();
        UserList.getInstance().changed();
    }

    public String getStatus() {
        return mStatus;
    }

    public Date getLastSeen() {
        return mLastSeen;
    }

    public Available getAvailable() {
        return this.mAvailable;
    }

    void setPresence(Presence.Type type, String status) {
        if (type == Presence.Type.available) {
            mAvailable = Available.YES;
            mLastSeen = new Date();
        }
        if (type == Presence.Type.unavailable) {
            mAvailable = Available.NO;
        }
        if (status != null) {
            mStatus = status;
        }
        UserList.getInstance().changed();
    }

    public void save() {
        Database db = Database.getInstance();
        Map<String, Object> set = new HashMap();
        set.put("jid", mJID);
        set.put("name", mName);
        set.put("status", mStatus);
        set.put("last_seen", mLastSeen);
        db.execUpdate(TABLE, set, mID);
    }

}
