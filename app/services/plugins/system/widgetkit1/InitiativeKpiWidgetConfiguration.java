package services.plugins.system.widgetkit1;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * The configuration for the initiative KPI widget.
 * 
 * @author Johann Kohler
 */
public class InitiativeKpiWidgetConfiguration implements Serializable {

    private static final long serialVersionUID = -5605992593738668866L;

    public String kpiUid;
    public Long portfolioEntryId;

    public InitiativeKpiWidgetConfiguration() {

    }

    /**
     * Serialize the configuration.
     */
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(out);
        os.writeObject(this);
        return out.toByteArray();
    }

    /**
     * Deserialize the configuration
     * 
     * @param data
     *            the date to deserialize
     */
    public void deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream is = new ObjectInputStream(in);
        InitiativeKpiWidgetConfiguration configuration = (InitiativeKpiWidgetConfiguration) is.readObject();
        this.kpiUid = configuration.kpiUid;
        this.portfolioEntryId = configuration.portfolioEntryId;
    }
}
