package com.github.danielflower.mavenplugins.release.report;/**
 * Created by ondrab on 19.5.17.
 */

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.Date;

/**
 * @author Ondrej.Bozek@clevermaps.cz
 **/
public class CommitData {

    private String shortMessage;
    private String message;
    private Date time;
    private String person;
    private String type;
    private int parents;

    public CommitData(RevCommit commit) {
        shortMessage = commit.getShortMessage();
        message = commit.getFullMessage();
        time = new Date(commit.getCommitTime() * 1000);
        person = commit.getAuthorIdent().getEmailAddress();
        type = Constants.typeString(commit.getType());
        parents = commit.getParentCount();
    }

    public String getShortMessage() {
        return shortMessage;
    }

    public void setShortMessage(String shortMessage) {
        this.shortMessage = shortMessage;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public String getPerson() {
        return person;
    }

    public void setPerson(String person) {
        this.person = person;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getParents() {
        return parents;
    }

    public void setParents(int parents) {
        this.parents = parents;
    }
}

