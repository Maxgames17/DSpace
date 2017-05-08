/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.webui.util;

import java.text.MessageFormat;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.content.IMetadataValue;
import org.dspace.core.I18nUtil;

public class ItemRefDisplayStrategy extends ASimpleDisplayStrategy
{
	/** log4j category */
    private static Logger log = Logger.getLogger(ItemRefDisplayStrategy.class);
    
    public String getMetadataDisplay(HttpServletRequest hrq, int limit,
            boolean viewFull, String browseType, int colIdx, UUID itemid, String field,
            List<IMetadataValue> metadataArray, boolean disableCrossLinks, boolean emph)
    {
        String metadata;
        // limit the number of records if this is the author field (if
        // -1, then the limit is the full list)
        boolean truncated = false;
        int loopLimit = metadataArray.size();
        if (limit != -1)
        {
            loopLimit = (limit > metadataArray.size() ? metadataArray.size()
                    : limit);
            truncated = (limit < metadataArray.size());
            log.debug("Limiting output of field " + field + " to "
                    + Integer.toString(loopLimit) + " from an original "
                    + Integer.toString(metadataArray.size()));
        }

        StringBuffer sb = new StringBuffer();
        for (int j = 0; j < loopLimit; j++)
        {
            sb.append(getDisplayForValue(hrq, field, metadataArray.get(j).getValue(), metadataArray.get(j).getAuthority(), itemid));
            if (j < (loopLimit - 1))
            {
                if (colIdx != -1) // we are showing metadata in a table row
                                  // (browse or item list)
                {
                    sb.append("; ");
                }
                else
                {
                    // we are in the item tag
                    sb.append("<br />");
                }
            }
        }
        if (truncated)
        {
            if (colIdx != -1)
            {
                sb.append("; ...");
            }
            else
            {
                sb.append("<br />...");
            }
        }

        if (colIdx != -1) // we are showing metadata in a table row (browse or
                          // item list)
        {
            metadata = (emph ? "<strong><em>" : "<em>") + sb.toString()
                    + (emph ? "</em></strong>" : "</em>");
        }
        else
        {
            // we are in the item tag
            metadata = (emph ? "<strong>" : "") + sb.toString()
                    + (emph ? "</strong>" : "");
        }

        return metadata;
    }
    
    private String getDisplayForValue(HttpServletRequest hrq, String field, String value, String authority, UUID itemid)
    {
    	if (StringUtils.isEmpty(authority)) return value;
        StringBuffer sb = new StringBuffer();
        String startLink = "<a href=\"" + hrq.getContextPath() + "/handle/"+ authority + "\">";
        String endLink = "</a>";
        sb.append(startLink);
		String valueWithIcon = value;
		try {
			valueWithIcon = MessageFormat.format(I18nUtil.getMessage("ItemRefDisplayStrategy.value-format-icon." + field, true), value);
		}
		catch (Exception e) {
			// get the default
			valueWithIcon = MessageFormat.format(I18nUtil.getMessage("ItemRefDisplayStrategy.value-format-icon"), value);
		}
		sb.append(valueWithIcon);
        sb.append(endLink);
        return sb.toString();
    }

}
