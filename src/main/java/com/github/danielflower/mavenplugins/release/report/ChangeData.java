package com.github.danielflower.mavenplugins.release.report;/**
 * Created by ondrab on 19.5.17.
 */

import org.eclipse.jgit.revwalk.RevCommit;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Ondrej.Bozek@clevermaps.cz
 **/
public class ChangeData {

    private List<CommitData> commitData = new ArrayList<>();


    public ChangeData() {
    }

    public CommitData addCommit(RevCommit revCommit) {
        assert (revCommit != null);
        CommitData result = new CommitData(revCommit);
        commitData.add(result);
        return result;
    }

    public boolean hasChanged() {
        return !commitData.isEmpty();
    }

    public List<CommitData> getCommitData() {
        return commitData;
    }

    public void setCommitData(List<CommitData> commitData) {
        this.commitData = commitData;
    }
}
