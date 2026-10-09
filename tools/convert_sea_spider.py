import json, math, re

with open('art/sources/cc0/sea_spider/sea_spider.bbmodel', 'r') as f:
    data = json.load(f)

elements = {e['uuid']: e for e in data.get('elements', [])}
groups_by_uuid = {g['uuid']: g for g in data.get('groups', [])}

root_node = data['outliner'][0]
root_uuid = root_node['uuid']

# Map parent relationships
parents = {}
children = {}
group_elements = {}

def traverse(node, p_uuid=None):
    if isinstance(node, dict):
        uid = node['uuid']
        parents[uid] = p_uuid
        children[uid] = []
        group_elements[uid] = []
        if p_uuid:
            children[p_uuid].append(uid)
        for c in node.get('children', []):
            traverse(c, uid)
    elif isinstance(node, str):
        if p_uuid:
            group_elements[p_uuid].append(node)

traverse(root_node)

def safe_float(v):
    try:
        return float(str(v).strip())
    except:
        return 0.0

anims = {a['name']: a for a in data.get('animations', [])}
idle_anim = anims.get('idle', {}).get('animators', {})

# Extract resting standing pose from IDLE at time=0
base_rot = {}
for k, v in idle_anim.items():
    name = v['name']
    for kf in v.get('keyframes', []):
        if kf.get('channel') == 'rotation' and float(kf.get('time', 0)) == 0.0:
            dp = kf.get('data_points', [{}])[0]
            base_rot[name] = [safe_float(dp.get('x', 0)), safe_float(dp.get('y', 0)), safe_float(dp.get('z', 0))]

def clean_name(name):
    name = re.sub(r'[^a-zA-Z0-9_]', '_', name)
    if name.startswith('_'):
        name = name.lstrip('_')
    if not name:
        name = 'part'
    if name[0].isdigit():
        name = 'part_' + name
    return name

# Output Java Model
model_lines = []
model_lines.append("""package pro.erez.interstice.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.CaveRiftSpiderEntity;

public final class CaveRiftSpiderModel extends HierarchicalModel<CaveRiftSpiderEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Interstice.ID, "cave_rift_spider"), "main");

    private final ModelPart root;

    public CaveRiftSpiderModel(ModelPart root) {
        this.root = root.getChild("root");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
""")

var_names = {}
name_counts = {}

def get_unique_var(g_name):
    clean = clean_name(g_name)
    count = name_counts.get(clean, 0) + 1
    name_counts[clean] = count
    return f"{clean}_{count}" if count > 1 else clean

def generate_part(uid, parent_var="parts"):
    grp = groups_by_uuid[uid]
    raw_name = grp['name']
    var_name = get_unique_var(raw_name)
    var_names[uid] = var_name

    g_orig = grp.get('origin', [0, 0, 0])
    p_orig = groups_by_uuid[parents[uid]]['origin'] if parents[uid] else [0, 24, 0]

    # Blockbench -> MC pose offset
    if parents[uid] is None:
        # root: lift body by 5 units so body hovers above floor as in resting pose
        ox = g_orig[0]
        oy = -(g_orig[1] - 24) - 5.0
        oz = g_orig[2]
    else:
        ox = g_orig[0] - p_orig[0]
        oy = -(g_orig[1] - p_orig[1])
        oz = g_orig[2] - p_orig[2]

    rot = grp.get('rotation', [0, 0, 0])
    b = base_rot.get(raw_name, [0.0, 0.0, 0.0])
    tot_x = rot[0] + b[0]
    tot_y = rot[1] + b[1]
    tot_z = rot[2] + b[2]

    # Minecraft Y is DOWN -> reflect X and Z Euler angles, preserve Y (yaw)
    rx = math.radians(-tot_x)
    ry = math.radians(tot_y)
    rz = math.radians(-tot_z)

    cubes_code = "CubeListBuilder.create()"
    for e_id in group_elements[uid]:
        elem = elements[e_id]
        f = elem.get('faces', {})
        u = int(f.get('east', {}).get('uv', [0])[0])
        v = int(f.get('up', {}).get('uv', [0, 0, 0, 0])[3])
        x1, y1, z1 = elem['from']
        x2, y2, z2 = elem['to']
        dx = x2 - x1
        dy = y2 - y1
        dz = z2 - z1
        bx = x1 - g_orig[0]
        by = -(y2 - g_orig[1])
        bz = z1 - g_orig[2]
        cubes_code += f"\n                .texOffs({u}, {v}).addBox({bx}F, {by}F, {bz}F, {dx}F, {dy}F, {dz}F)"

    has_rot = (abs(rx) > 1e-4 or abs(ry) > 1e-4 or abs(rz) > 1e-4)
    if has_rot:
        pose_code = f"PartPose.offsetAndRotation({ox}F, {oy}F, {oz}F, {rx:.5f}F, {ry:.5f}F, {rz:.5f}F)"
    else:
        pose_code = f"PartPose.offset({ox}F, {oy}F, {oz}F)"

    model_lines.append(f"        PartDefinition {var_name} = {parent_var}.addOrReplaceChild(\"{raw_name}\",\n                {cubes_code},\n                {pose_code});")

    for child_uid in children[uid]:
        generate_part(child_uid, var_name)

generate_part(root_uuid)

model_lines.append("""
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(CaveRiftSpiderEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        this.animate(entity.idleAnimationState, CaveRiftSpiderAnimation.IDLE, ageInTicks, 1.0F);
        this.animateWalk(CaveRiftSpiderAnimation.MOVE, limbSwing, limbSwingAmount, 2.5F, 2.5F);

        // Adjust body orientation when climbing walls or ceiling
        if (entity.isClimbingWall()) {
            this.root.xRot = (float) Math.toRadians(-90.0);
        } else if (entity.isClimbingCeiling()) {
            this.root.zRot = (float) Math.toRadians(180.0);
        }
    }
}
""")

with open('src/main/java/pro/erez/interstice/entity/client/CaveRiftSpiderModel.java', 'w') as f:
    f.write("\n".join(model_lines))

print("CaveRiftSpiderModel.java written!")
