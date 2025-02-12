package com.aliyun.openservices.log.response;

import java.util.Map;

import com.aliyun.openservices.log.common.Consts;
import com.aliyun.openservices.log.common.ProjectQuota;
import com.aliyun.openservices.log.exception.LogException;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;

public class GetProjectResponse extends Response {

    /**
     *
     */
    private static final long serialVersionUID = 1938728647331317823L;

    private String mDescription = "";
    private String mStatus = "";
    private String mResourceGroup = "";

    private String mRegion = "";
    private String mOwner = "";
    private ProjectQuota quota;

    public GetProjectResponse(Map<String, String> headers) {
        super(headers);
    }

    public void FromJsonObject(JSONObject obj) throws LogException {
        try {
            mDescription = obj.getString("description");
            mStatus = obj.getString("status");
            mResourceGroup = obj.getString("resourceGroupId");
            mRegion = obj.getString("region");
            mOwner = obj.getString("owner");
            quota = ProjectQuota.parseFromJSON(obj.getJSONObject(Consts.CONST_QUOTA));
        } catch (JSONException e) {
            throw new LogException("InvalidErrorResponse", e.getMessage(),
                    GetRequestId());
        }
    }

    public String GetProjectDescription() {
        return mDescription;
    }

    public String GetProjectStatus() {
        return mStatus;
    }

    public String getResourceGroupId() {
        return mResourceGroup;
    }

    public String GetProjectRegion() {
        return mRegion;
    }

    public String GetProjectOwner() {
        return mOwner;
    }

    public ProjectQuota getQuota() {
        return quota;
    }

    public void setQuota(ProjectQuota quota) {
        this.quota = quota;
    }
}
