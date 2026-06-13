package Bears.BearEnvironment;

import Bears.BearAgent.BearAgent;
import MASInterface.Environment.Coords;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Binary, gzip-compressed checkpoint of a full simulation state: every cell's
 * type/food/danger plus every live bear's complete internal state and position.
 *
 * <p>Purpose: the model needs a multi-year "spin-up" before the founding
 * population settles past its initialization transient. Saving the spun-up
 * state once lets every later scenario start from that equilibrated point
 * instead of re-running the spin-up. A loaded snapshot fully replaces both the
 * habitat grid and the founding cohort, so it can be used in place of
 * {@code MAP_SOURCE} / random founders.</p>
 *
 * <p>Format (v1): magic, version, mapLength, tick, lastAgentId, then one record
 * per cell (type ordinal as byte, food and danger as float), then an agent
 * count followed by one record per live bear. Floats keep the ~1M-cell grid
 * around 9 MB before gzip.</p>
 */
public final class BearSnapshot {

    private static final int MAGIC = 0x42534E50; // "BSNP"
    private static final int VERSION = 1;
    private static final BearCellType[] TYPES = BearCellType.values();

    /** Restored state plus the bookkeeping the simulation needs to resume. */
    public static final class Loaded {
        public final BearState state;
        public final Map<Integer, BearAgent> agentsById;
        public final int lastAgentId;
        public final long tick;
        public Loaded(BearState state, Map<Integer, BearAgent> agentsById, int lastAgentId, long tick) {
            this.state = state;
            this.agentsById = agentsById;
            this.lastAgentId = lastAgentId;
            this.tick = tick;
        }
    }

    private BearSnapshot() {}

    public static void save(Path file, BearState state, Map<Integer, BearAgent> agentsById,
                            int lastAgentId, long tick) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        int mapLength = state.mapLength();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)), 1 << 16))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(mapLength);
            out.writeLong(tick);
            out.writeInt(lastAgentId);

            for (int x = 0; x < mapLength; x++) {
                for (int y = 0; y < mapLength; y++) {
                    BearCell cell = state.getBearCell(x, y);
                    BearCellType type = cell != null ? cell.bearCellType() : BearCellType.NONE;
                    out.writeByte(type.ordinal());
                    out.writeFloat(cell != null ? (float) cell.food() : -1f);
                    out.writeFloat(cell != null ? (float) cell.danger() : -1f);
                }
            }

            out.writeInt(agentsById.size());
            for (BearAgent a : agentsById.values()) {
                Coords c = state.getAgentCoords(a.getId());
                out.writeInt(a.getId());
                out.writeByte(a.getGender().ordinal());
                out.writeDouble(a.getAge());
                out.writeDouble(a.getSatiety());
                out.writeDouble(a.getReproductionCooldownYearsRemaining());
                out.writeDouble(a.getGestationPeriod());
                out.writeBoolean(a.isPregnant());
                out.writeInt(a.getHomeX());
                out.writeInt(a.getHomeY());
                out.writeInt(c.x);
                out.writeInt(c.y);
            }
        }
    }

    public static Loaded load(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(
                new BufferedInputStream(Files.newInputStream(file)), 1 << 16))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Not a bear snapshot file: " + file);
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported snapshot version " + version + " in " + file);
            }
            int mapLength = in.readInt();
            long tick = in.readLong();
            int lastAgentId = in.readInt();

            BearCellType[][] types = new BearCellType[mapLength][mapLength];
            double[][] food = new double[mapLength][mapLength];
            double[][] danger = new double[mapLength][mapLength];
            for (int x = 0; x < mapLength; x++) {
                for (int y = 0; y < mapLength; y++) {
                    types[x][y] = TYPES[in.readUnsignedByte()];
                    food[x][y] = in.readFloat();
                    danger[x][y] = in.readFloat();
                }
            }

            BearState state = BearState.getInitStateFromSnapshot(mapLength, types, food, danger);

            int agentCount = in.readInt();
            Map<Integer, BearAgent> agentsById = new HashMap<>(Math.max(16, agentCount * 2));
            for (int i = 0; i < agentCount; i++) {
                int id = in.readInt();
                BearAgent.Gender gender = BearAgent.Gender.values()[in.readUnsignedByte()];
                double age = in.readDouble();
                double satiety = in.readDouble();
                double cooldown = in.readDouble();
                double gestation = in.readDouble();
                boolean pregnant = in.readBoolean();
                int homeX = in.readInt();
                int homeY = in.readInt();
                int cx = in.readInt();
                int cy = in.readInt();

                BearAgent agent = BearAgent.fromSnapshot(id, gender, age, satiety,
                        cooldown, gestation, pregnant, homeX, homeY);
                agentsById.put(id, agent);
                state.placeSnapshotAgent(id, gender, cx, cy);
            }

            return new Loaded(state, agentsById, lastAgentId, tick);
        }
    }
}
