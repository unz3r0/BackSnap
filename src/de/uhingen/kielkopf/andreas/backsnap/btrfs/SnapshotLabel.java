/**
 * 
 */
package de.uhingen.kielkopf.andreas.backsnap.btrfs;

import java.awt.Color;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.border.*;

/**
 * @author Andreas Kielkopf
 *
 */
public class SnapshotLabel extends JLabel {
   private static final long                                   serialVersionUID=5111240176198425385L;
   private static ConcurrentSkipListMap<String, SnapshotLabel> labels          =new ConcurrentSkipListMap<>();
   public Snapshot                                             snapshot;
   public SnapshotLabel() {
      initialize();
   }
   /**
    * @param snapshot
    */
   public SnapshotLabel(Snapshot snapshot1) {
      this();
      if (snapshot1 == null)
         return;
      snapshot=snapshot1;
      String name=snapshot.dirName();
      setBackground(snapshot.isBackup() ? aktuellColor : snapshotColor);
      setText(name);
   }
   final static Color        unknownColor =Color.RED.darker();
   final static Color        snapshotColor=Color.YELLOW.brighter();
   public final static Color aktuellColor =Color.YELLOW.darker();
   public final static Color delete2Color =Color.ORANGE;
   public final static Color backupColor  =Color.GREEN.brighter(); // muß bleiben
   public final static Color keepColor    =Color.CYAN.brighter();
   public final static Color deleteColor  =Color.RED.brighter();   // darf weg
   public final static Color naheColor    =Color.ORANGE.brighter();
   private void initialize() {
      setBorder(new CompoundBorder(new LineBorder(new Color(0, 0, 0), 2, true), new EmptyBorder(5, 5, 5, 5)));
      setOpaque(true);
      setBackground(unknownColor);
      setHorizontalAlignment(SwingConstants.CENTER);
      setText("name");
   }
   /**
    * @param snapshot2
    * @return
    */
   public static SnapshotLabel getSnapshotLabel(Snapshot snapshot2) {
      if (labels.get(snapshot2.uuid()) instanceof SnapshotLabel sl) {
         sl.setBackground(snapshot2.isBackup() ? aktuellColor : snapshotColor);
         return sl;
      }
      SnapshotLabel sl=new SnapshotLabel(snapshot2);
      labels.put(snapshot2.uuid(), sl);
      return sl;
   }
}
