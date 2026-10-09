import json

with open('art/sources/cc0/sea_spider/sea_spider.bbmodel', 'r') as f:
    data = json.load(f)

def safe_float(v):
    try:
        return float(str(v).strip())
    except:
        return 0.0

anims = {a['name']: a for a in data.get('animations', [])}
idle_anim = anims.get('idle', {}).get('animators', {})

# Base rotations baked into PartPose (from IDLE at t=0)
base_rot = {}
for k, v in idle_anim.items():
    name = v['name']
    for kf in v.get('keyframes', []):
        if kf.get('channel') == 'rotation' and float(kf.get('time', 0)) == 0.0:
            dp = kf.get('data_points', [{}])[0]
            base_rot[name] = [safe_float(dp.get('x', 0)), safe_float(dp.get('y', 0)), safe_float(dp.get('z', 0))]

lines = []
lines.append("""package pro.erez.interstice.entity.client;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

public final class CaveRiftSpiderAnimation {
    private CaveRiftSpiderAnimation() {}
""")

for anim in data.get('animations', []):
    anim_name = anim['name'].upper()
    length = float(anim.get('length', 1.0))
    loop = anim.get('loop', 'loop') == 'loop'
    builder_start = f"AnimationDefinition.Builder.withLength({length}F)"
    if loop:
        builder_start += ".looping()"

    lines.append(f"    public static final AnimationDefinition {anim_name} = {builder_start}")

    for a_id, a_data in anim.get('animators', {}).items():
        bone_name = a_data.get('name')
        kfs = a_data.get('keyframes', [])
        if not kfs:
            continue

        by_ch = {}
        for kf in kfs:
            by_ch.setdefault(kf.get('channel'), []).append(kf)

        for ch in ['rotation', 'position']:
            ch_kfs = by_ch.get(ch, [])
            if not ch_kfs:
                continue

            ch_kfs.sort(key=lambda k: float(k['time']))
            target = "AnimationChannel.Targets.ROTATION" if ch == 'rotation' else "AnimationChannel.Targets.POSITION"

            kf_lines = []
            has_non_zero = False
            for kf in ch_kfs:
                time = float(kf['time'])
                interp_str = kf.get('interpolation', 'linear').upper()
                interp = "AnimationChannel.Interpolations.CATMULLROM" if interp_str == 'CATMULLROM' else "AnimationChannel.Interpolations.LINEAR"
                dp = kf.get('data_points', [{}])[0]
                x = safe_float(dp.get('x', 0))
                y = safe_float(dp.get('y', 0))
                z = safe_float(dp.get('z', 0))

                if ch == 'rotation':
                    b = base_rot.get(bone_name, [0.0, 0.0, 0.0])
                    dx = x - b[0]
                    dy = y - b[1]
                    dz = z - b[2]
                    nx = 0.0 if abs(dx) < 1e-4 else -dx
                    ny = 0.0 if abs(dy) < 1e-4 else dy
                    nz = 0.0 if abs(dz) < 1e-4 else -dz
                    if abs(nx) > 1e-4 or abs(ny) > 1e-4 or abs(nz) > 1e-4:
                        has_non_zero = True
                    vec_code = f"KeyframeAnimations.degreeVec({nx:.2f}F, {ny:.2f}F, {nz:.2f}F)"
                else:
                    base_py = 5.0 if bone_name == 'root' else 0.0
                    dy = y - base_py
                    ny = 0.0 if abs(dy) < 1e-4 else -dy
                    if abs(x) > 1e-4 or abs(ny) > 1e-4 or abs(z) > 1e-4:
                        has_non_zero = True
                    vec_code = f"KeyframeAnimations.posVec({x:.2f}F, {ny:.2f}F, {z:.2f}F)"

                kf_lines.append(f"                    new Keyframe({time:.3f}F, {vec_code}, {interp})")

            # Only add channels that actually have animated motion!
            if has_non_zero:
                lines.append(f"            .addAnimation(\"{bone_name}\", new AnimationChannel({target},")
                lines.append(",\n".join(kf_lines) + "))")

    lines.append("            .build();\n")

lines.append("}\n")

with open('src/main/java/pro/erez/interstice/entity/client/CaveRiftSpiderAnimation.java', 'w') as f:
    f.write("\n".join(lines))

print("CaveRiftSpiderAnimation.java written!")
