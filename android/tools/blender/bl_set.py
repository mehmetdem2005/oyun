# bl_set.py v2 — TAM SET (14 varlık), 3/4 izometrik kamera, PÜRÜZSÜZ yüzeyler
# Değişiklik gerekçesi: kullanıcı geri bildirimi → displace kabalığı kısıldı,
# shade_smooth eklendi, kamera önden-düz yerine sağ-önden 34°/31° (3B hissi).
import bpy, os
from math import radians, sin, cos

OUT = "/home/claude/render/raw/"
os.makedirs(OUT, exist_ok=True)

def clear():
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)

def cube(name, sx, sy, sz, x, y, z):
    bpy.ops.mesh.primitive_cube_add(size=1, location=(x, y, z))
    o = bpy.context.object; o.name = name; o.scale = (sx, sy, sz)
    bpy.ops.object.transform_apply(scale=True)
    return o

def cyl(name, r, d, x, y, z, rx=0.0, ry=0.0, rz=0.0, verts=24):
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=d,
        location=(x, y, z), rotation=(rx, ry, rz))
    o = bpy.context.object; o.name = name
    return o

def cone(name, r, d, x, y, z, verts=24):
    bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r, depth=d, location=(x, y, z))
    o = bpy.context.object; o.name = name
    return o

def ico(name, r, x, y, z, sx=1, sy=1, sz=1, sub=3):
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=sub, radius=r, location=(x, y, z))
    o = bpy.context.object; o.name = name; o.scale = (sx, sy, sz)
    bpy.ops.object.transform_apply(scale=True)
    return o

def smooth(o, deg=46):
    for f in o.data.polygons: f.use_smooth = True
    try:
        o.data.use_auto_smooth = True
        o.data.auto_smooth_angle = radians(deg)
    except Exception:
        pass

def relief(o, strength=0.012, noise=0.55, bevel=0.018, lv=3):
    # PÜRÜZSÜZ sürüm: varsayılan güç yarıdan az — silüet düz kalır, taş "nefes alır"
    if bevel > 0:
        bv = o.modifiers.new("bev", 'BEVEL'); bv.width = bevel; bv.segments = 2
    sub = o.modifiers.new("sub", 'SUBSURF'); sub.subdivision_type = 'SIMPLE'
    sub.levels = lv; sub.render_levels = lv
    tex = bpy.data.textures.new("rg", 'CLOUDS'); tex.noise_scale = noise
    dm = o.modifiers.new("disp", 'DISPLACE'); dm.texture = tex; dm.strength = strength

# ── materyaller ────────────────────────────────────────────────────
def mat_stone():
    m = bpy.data.materials.new("Stone"); m.use_nodes = True
    nt = m.node_tree; n = nt.nodes; ln = nt.links
    b = n["Principled BSDF"]; b.inputs["Roughness"].default_value = 0.85
    tc = n.new("ShaderNodeTexCoord"); mp = n.new("ShaderNodeMapping")
    mp.inputs["Scale"].default_value = (1.6, 1.6, 1.6)
    mp.inputs["Rotation"].default_value = (radians(90), 0, 0)
    br = n.new("ShaderNodeTexBrick")
    br.inputs["Color1"].default_value = (0.165, 0.180, 0.214, 1)
    br.inputs["Color2"].default_value = (0.090, 0.100, 0.128, 1)
    br.inputs["Mortar"].default_value = (0.030, 0.034, 0.046, 1)
    br.inputs["Scale"].default_value = 1.7
    br.inputs["Mortar Size"].default_value = 0.026
    ns = n.new("ShaderNodeTexNoise"); ns.inputs["Scale"].default_value = 7.0
    mx = n.new("ShaderNodeMixRGB"); mx.blend_type = 'MULTIPLY'
    mx.inputs["Fac"].default_value = 0.22
    geo = n.new("ShaderNodeNewGeometry"); rp = n.new("ShaderNodeValToRGB")
    rp.color_ramp.elements[0].position = 0.50
    rp.color_ramp.elements[1].position = 0.62
    me = n.new("ShaderNodeMixRGB")
    me.inputs["Color2"].default_value = (0.42, 0.45, 0.51, 1)
    bp = n.new("ShaderNodeBump"); bp.inputs["Strength"].default_value = 0.30
    ln.new(tc.outputs["Object"], mp.inputs["Vector"])
    ln.new(mp.outputs["Vector"], br.inputs["Vector"])
    ln.new(mp.outputs["Vector"], ns.inputs["Vector"])
    ln.new(br.outputs["Color"], mx.inputs["Color1"])
    ln.new(ns.outputs["Fac"], mx.inputs["Color2"])
    ln.new(mx.outputs["Color"], me.inputs["Color1"])
    ln.new(geo.outputs["Pointiness"], rp.inputs["Fac"])
    ln.new(rp.outputs["Color"], me.inputs["Fac"])
    ln.new(me.outputs["Color"], b.inputs["Base Color"])
    ln.new(br.outputs["Fac"], bp.inputs["Height"])
    ln.new(bp.outputs["Normal"], b.inputs["Normal"])
    return m

def mat_simple(name, col, rough=0.8, metal=0.0, emis=0.0):
    m = bpy.data.materials.new(name); m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (col[0], col[1], col[2], 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    if emis > 0:
        try:
            b.inputs["Emission Color"].default_value = (col[0], col[1], col[2], 1)
            b.inputs["Emission Strength"].default_value = emis
        except Exception:
            pass
    return m

def mat_wood():
    m = bpy.data.materials.new("Wood"); m.use_nodes = True
    nt = m.node_tree; n = nt.nodes; ln = nt.links
    b = n["Principled BSDF"]; b.inputs["Roughness"].default_value = 0.82
    tc = n.new("ShaderNodeTexCoord"); mp = n.new("ShaderNodeMapping")
    mp.inputs["Rotation"].default_value = (radians(90), 0, 0)
    mp.inputs["Scale"].default_value = (2.2, 2.2, 2.2)
    br = n.new("ShaderNodeTexBrick")
    br.inputs["Color1"].default_value = (0.118, 0.066, 0.030, 1)
    br.inputs["Color2"].default_value = (0.088, 0.046, 0.021, 1)
    br.inputs["Mortar"].default_value = (0.020, 0.011, 0.006, 1)
    br.inputs["Scale"].default_value = 1.0
    br.inputs["Mortar Size"].default_value = 0.018
    try:
        br.inputs["Brick Width"].default_value = 0.24
        br.inputs["Row Height"].default_value = 3.6
    except Exception:
        pass
    ns = n.new("ShaderNodeTexNoise"); ns.inputs["Scale"].default_value = 14.0
    bp = n.new("ShaderNodeBump"); bp.inputs["Strength"].default_value = 0.18
    ln.new(tc.outputs["Object"], mp.inputs["Vector"])
    ln.new(mp.outputs["Vector"], br.inputs["Vector"])
    ln.new(mp.outputs["Vector"], ns.inputs["Vector"])
    ln.new(br.outputs["Color"], b.inputs["Base Color"])
    ln.new(ns.outputs["Fac"], bp.inputs["Height"])
    ln.new(bp.outputs["Normal"], b.inputs["Normal"])
    return m

def mat_leaf():
    m = bpy.data.materials.new("Leaf"); m.use_nodes = True
    nt = m.node_tree; n = nt.nodes; ln = nt.links
    b = n["Principled BSDF"]; b.inputs["Roughness"].default_value = 0.9
    ns = n.new("ShaderNodeTexNoise"); ns.inputs["Scale"].default_value = 2.2
    rp = n.new("ShaderNodeValToRGB")
    rp.color_ramp.elements[0].color = (0.030, 0.068, 0.034, 1)
    rp.color_ramp.elements[1].color = (0.055, 0.112, 0.052, 1)
    ln.new(ns.outputs["Fac"], rp.inputs["Fac"])
    ln.new(rp.outputs["Color"], b.inputs["Base Color"])
    return m
def mat_rock():
    m = bpy.data.materials.new("Rock"); m.use_nodes = True
    nt = m.node_tree; n = nt.nodes; ln = nt.links
    b = n["Principled BSDF"]; b.inputs["Roughness"].default_value = 0.95
    geo = n.new("ShaderNodeNewGeometry"); rp = n.new("ShaderNodeValToRGB")
    rp.color_ramp.elements[0].position = 0.52
    rp.color_ramp.elements[0].color = (0.030, 0.034, 0.045, 1)
    rp.color_ramp.elements[1].position = 0.64
    rp.color_ramp.elements[1].color = (0.150, 0.165, 0.195, 1)
    ln.new(geo.outputs["Pointiness"], rp.inputs["Fac"])
    ln.new(rp.outputs["Color"], b.inputs["Base Color"])
    return m
def banner(x, y, z, w=0.20, h=0.36):
    bpy.ops.mesh.primitive_grid_add(x_subdivisions=14, y_subdivisions=3, size=1)
    ban = bpy.context.object; ban.scale = (w, h, 1)
    bpy.ops.object.transform_apply(scale=True)
    for v in ban.data.vertices:
        v.co.z = sin(v.co.x * 22 + v.co.y * 4) * 0.018
    ban.rotation_euler = (radians(90), 0, 0); ban.location = (x, y, z)
    smooth(ban, 80)
    ban.data.materials.append(mat_simple("Blue", (0.044, 0.110, 0.342), 0.62))
    bpy.ops.mesh.primitive_cube_add(size=1, location=(x, y - 0.030, z))
    d = bpy.context.object
    d.scale = (0.085, 0.012, 0.085); d.rotation_euler = (0, radians(45), 0)
    d.data.materials.append(mat_simple("Gold", (0.66, 0.41, 0.085), 0.34, 0.9))
    rod = cyl("rod", 0.018, w * 2.7, x, y + 0.006, z + h + 0.045, ry=radians(90))
    smooth(rod)
    rod.data.materials.append(mat_simple("WoodR", (0.13, 0.066, 0.028)))

# ── 3/4 İZOMETRİK RİG: sağ-önden 34°, yukarıdan 31° → 3B hissi ─────
def rig(ortho, ch):
    def sun(rot, col, e):
        bpy.ops.object.light_add(type='SUN', rotation=rot)
        L = bpy.context.object; L.data.color = col; L.data.energy = e; L.data.angle = 0.3
    sun((radians(52), 0, radians(-38)), (1.0, 0.90, 0.74), 6.0)   # sıcak key sol-üst
    sun((radians(65), 0, radians(48)), (0.55, 0.66, 1.0), 0.9)    # soğuk dolgu sağ
    sun((radians(-35), 0, radians(180)), (0.9, 0.95, 1.0), 2.6)   # tepe rim
    S = bpy.context.scene
    S.world.use_nodes = True
    bg = S.world.node_tree.nodes["Background"]
    bg.inputs["Color"].default_value = (0.05, 0.06, 0.09, 1)
    bg.inputs["Strength"].default_value = 0.10
    az, el, dist = radians(34), radians(31), 9.0
    cx = dist * sin(az) * cos(el)
    cy = -dist * cos(az) * cos(el)
    cz = ch + dist * sin(el)
    bpy.ops.object.empty_add(location=(0, 0, ch))
    tgt = bpy.context.object
    bpy.ops.object.camera_add(location=(cx, cy, cz))
    cam = bpy.context.object; cam.data.type = 'ORTHO'
    cam.data.ortho_scale = ortho
    con = cam.constraints.new('TRACK_TO'); con.target = tgt
    con.track_axis = 'TRACK_NEGATIVE_Z'; con.up_axis = 'UP_Y'
    S.camera = cam

def render(name):
    S = bpy.context.scene
    S.render.engine = 'CYCLES'; S.cycles.device = 'CPU'
    S.cycles.samples = 44; S.cycles.use_adaptive_sampling = True
    S.cycles.max_bounces = 4; S.cycles.diffuse_bounces = 2; S.cycles.glossy_bounces = 2
    try:
        S.cycles.use_denoising = True; S.cycles.denoiser = 'OPENIMAGEDENOISE'
    except Exception:
        S.cycles.use_denoising = False
    S.render.film_transparent = True
    S.render.resolution_x = 560; S.render.resolution_y = 560
    S.render.image_settings.color_mode = 'RGBA'
    S.render.filepath = OUT + name + ".png"
    bpy.ops.render.render(write_still=True)
    print("RENDER OK:", name)

# ── YAPILAR ────────────────────────────────────────────────────────
def b_stonewall():
    sm = mat_stone()
    for o in (cube("body", 2.2, 0.7, 1.5, 0, 0, 0.75),
              cube("ledge", 2.34, 0.8, 0.10, 0, 0, 1.55),
              cube("m1", 0.36, 0.74, 0.24, -0.78, 0, 1.72),
              cube("m2", 0.36, 0.74, 0.24, 0.0, 0, 1.72),
              cube("m3", 0.36, 0.74, 0.24, 0.78, 0, 1.72)):
        relief(o, 0.014, 0.55, 0.018, 4); o.data.materials.append(sm)
    banner(0, -0.40, 1.02)
    rig(3.3, 0.95); render("build_stonewall")

def b_wall_palisade():
    wm = mat_wood(); im = mat_simple("Iron", (0.045, 0.050, 0.060), 0.5, 0.8)
    for i in range(7):
        h = 1.42 + 0.14 * sin(i * 2.1)
        x = -0.90 + i * 0.30
        o = cyl("log%d" % i, 0.145, h, x, 0, h / 2, verts=16)
        o.rotation_euler = (0.04 * sin(i * 1.7), 0.03 * cos(i * 2.3), 0)
        smooth(o); o.data.materials.append(wm)
        t = cone("tip%d" % i, 0.145, 0.30, x, 0, h + 0.14, verts=16)
        t.rotation_euler = o.rotation_euler
        smooth(t, 50); t.data.materials.append(wm)
    for z in (0.52, 1.02):
        b = cube("band", 1.98, 0.36, 0.10, 0, 0, z)
        b.data.materials.append(im)
    rig(3.1, 0.85); render("build_wall")

def b_gate(open_):
    sm = mat_stone(); wm = mat_wood()
    im = mat_simple("Iron", (0.045, 0.050, 0.060), 0.5, 0.8)
    for sx in (-0.86, 0.86):
        p = cube("pil", 0.36, 0.62, 1.70, sx, 0, 0.85)
        relief(p, 0.012); p.data.materials.append(sm)
    l = cube("lin", 2.10, 0.66, 0.36, 0, 0, 1.88)
    relief(l, 0.012); l.data.materials.append(sm)
    for mx in (-0.55, 0.0, 0.55):
        mm = cube("mer", 0.30, 0.60, 0.20, mx, 0, 2.16)
        relief(mm, 0.010, 0.6, 0.012, 3); mm.data.materials.append(sm)
    if open_:
        dk = cube("dark", 1.32, 0.06, 1.40, 0, 0.10, 0.72)
        dk.data.materials.append(mat_simple("Dark", (0.012, 0.014, 0.020), 1.0))
    else:
        d = cube("door", 1.32, 0.10, 1.42, 0, -0.16, 0.73)
        d.data.materials.append(wm)
        for z in (0.42, 1.04):
            b2 = cube("band", 1.34, 0.13, 0.10, 0, -0.18, z)
            b2.data.materials.append(im)
        s2 = cube("seam", 0.035, 0.12, 1.40, 0, -0.17, 0.73)
        s2.data.materials.append(im)
    rig(3.3, 1.05)
    render("build_gate_open" if open_ else "build_gate")

def b_keep():
    sm = mat_stone()
    b = cube("body", 1.40, 0.95, 2.30, 0, 0, 1.15)
    relief(b, 0.014); b.data.materials.append(sm)
    l = cube("ledge", 1.60, 1.10, 0.10, 0, 0, 2.34)
    relief(l, 0.012, 0.6, 0.012, 3); l.data.materials.append(sm)
    for mx in (-0.52, -0.17, 0.17, 0.52):
        m2 = cube("mer", 0.24, 1.02, 0.22, mx, 0, 2.50)
        relief(m2, 0.010, 0.6, 0.010, 3); m2.data.materials.append(sm)
    sl = cube("slit", 0.10, 0.20, 0.55, 0, -0.46, 1.55)
    sl.data.materials.append(mat_simple("Dark", (0.012, 0.014, 0.020), 1.0))
    banner(0, -0.50, 0.85)
    rig(3.9, 1.30); render("build_keep")

def b_ballista():
    sm = mat_stone(); wm = mat_wood()
    im = mat_simple("Iron", (0.045, 0.050, 0.060), 0.5, 0.8)
    pl = cyl("plat", 0.88, 0.34, 0, 0, 0.17, verts=8)
    relief(pl, 0.012, 0.7, 0.014, 3); pl.data.materials.append(sm)
    ps = cube("post", 0.22, 0.22, 0.85, 0, 0, 0.76); ps.data.materials.append(wm)
    ax = cube("axle", 0.95, 0.18, 0.16, 0, 0, 1.22); ax.data.materials.append(wm)
    a1 = cyl("arm1", 0.055, 0.95, -0.46, 0, 1.42, ry=radians(-38))
    smooth(a1); a1.data.materials.append(wm)
    a2 = cyl("arm2", 0.055, 0.95, 0.46, 0, 1.42, ry=radians(38))
    smooth(a2); a2.data.materials.append(wm)
    st = cyl("string", 0.016, 1.52, 0, 0, 1.78, ry=radians(90))
    st.data.materials.append(im)
    bt = cyl("bolt", 0.035, 0.85, 0, -0.14, 1.30, rx=radians(78))
    smooth(bt); bt.data.materials.append(im)
    rig(3.2, 0.95); render("build_ballista")

def b_heart():
    pm = mat_simple("Ped", (0.165, 0.175, 0.195), 0.85)
    t1 = cube("t1", 1.00, 1.00, 0.22, 0, 0, 0.11); relief(t1, 0.008); t1.data.materials.append(pm)
    t2 = cube("t2", 0.68, 0.68, 0.20, 0, 0, 0.32); relief(t2, 0.008); t2.data.materials.append(pm)
    d = ico("gem", 0.20, 0, 0, 0.97, 1.0, 1.0, 1.38, sub=1)  # 20 yüzlü kristal
    d.data.materials.append(mat_simple("Gold", (0.72, 0.46, 0.11), 0.28, 0.9, 0.3))
    rig(2.0, 0.55); render("heart")

# ── DOĞA ───────────────────────────────────────────────────────────
def b_tree1():
    tm = mat_simple("Trunk", (0.10, 0.055, 0.026), 0.9)
    lm = mat_leaf()
    t = cyl("trunk", 0.13, 0.75, 0, 0, 0.37, verts=12)
    smooth(t); t.data.materials.append(tm)
    for (r, d, z) in ((0.80, 0.85, 0.95), (0.60, 0.75, 1.45), (0.40, 0.62, 1.92)):
        c2 = cone("c", r, d, 0, 0, z, verts=28)
        smooth(c2, 50); c2.data.materials.append(lm)
    rig(3.3, 1.15); render("tree_1")

def b_tree2():
    tm = mat_simple("Trunk", (0.10, 0.055, 0.026), 0.9)
    lm = mat_leaf()
    t = cyl("trunk", 0.14, 0.95, 0, 0, 0.47, verts=12)
    smooth(t); t.data.materials.append(tm)
    for (r, x, y, z) in ((0.60, 0, 0, 1.32), (0.44, -0.34, 0.05, 1.04),
                         (0.42, 0.32, -0.04, 1.10), (0.36, 0.05, 0.06, 1.62)):
        b2 = ico("blob", r, x, y, z, sub=3)
        smooth(b2, 80); b2.data.materials.append(lm)
    rig(3.0, 1.00); render("tree_2")

def b_tree3():
    tm = mat_simple("Trunk", (0.10, 0.055, 0.026), 0.9)
    lm = mat_leaf()
    t = cyl("trunk", 0.11, 1.00, 0, 0, 0.50, verts=12)
    smooth(t); t.data.materials.append(tm)
    for (r, d, z) in ((0.55, 0.70, 1.00), (0.45, 0.62, 1.42),
                      (0.35, 0.56, 1.80), (0.25, 0.50, 2.14)):
        c2 = cone("c", r, d, 0, 0, z, verts=24)
        smooth(c2, 50); c2.data.materials.append(lm)
    rig(3.7, 1.25); render("tree_3")

def crag(r, x, y, z, sx, sy, sz, dstr=0.30, dscale=1.00):
    o = ico("r", r, x, y, z, sx, sy, sz, sub=2)
    tex = bpy.data.textures.new("rk", 'CLOUDS'); tex.noise_scale = dscale
    dm = o.modifiers.new("disp", 'DISPLACE'); dm.texture = tex; dm.strength = dstr
    dc = o.modifiers.new("dec", 'DECIMATE'); dc.decimate_type = 'DISSOLVE'
    dc.angle_limit = radians(15)
    bv = o.modifiers.new("bev", 'BEVEL'); bv.width = 0.018; bv.segments = 2
    o.data.materials.append(mat_rock())
    return o

def b_rock1():
    o = crag(0.62, 0, 0, 0.50, 1.30, 0.95, 0.80)
    o.rotation_euler = (0, 0, radians(25))
    rig(2.2, 0.45); render("rock_1")
def b_rock2():
    crag(0.55, -0.18, 0, 0.40, 1.30, 1.00, 0.74)
    crag(0.34, 0.50, -0.08, 0.28, 1.05, 1.00, 0.88, dscale=0.85)
    rig(2.3, 0.40); render("rock_2")
def b_rock3():
    o = crag(0.50, 0, 0, 0.68, 0.95, 0.82, 1.45, dstr=0.32)
    o.rotation_euler = (0, radians(6), radians(14))
    rig(2.7, 0.72); render("rock_3")
def b_bush():
    lm = mat_leaf()
    a = ico("a", 0.42, 0, 0, 0.34, 1.25, 1.05, 0.74, sub=3)
    smooth(a, 80); a.data.materials.append(lm)
    b = ico("b", 0.24, -0.40, 0.04, 0.24, 1, 1, 0.9, sub=3)
    smooth(b, 80); b.data.materials.append(lm)
    c2 = ico("c", 0.22, 0.40, -0.05, 0.22, 1, 1, 0.9, sub=3)
    smooth(c2, 80); c2.data.materials.append(lm)
    rig(1.8, 0.34); render("bush")

JOBS = [
    ("build_stonewall", b_stonewall),
    ("build_wall", b_wall_palisade),
    ("build_gate", lambda: b_gate(False)),
    ("build_gate_open", lambda: b_gate(True)),
    ("build_keep", b_keep),
    ("build_ballista", b_ballista),
    ("heart", b_heart),
    ("tree_1", b_tree1),
    ("tree_2", b_tree2),
    ("tree_3", b_tree3),
    ("rock_1", b_rock1),
    ("rock_2", b_rock2),
    ("rock_3", b_rock3),
    ("bush", b_bush),
]
for name, fn in JOBS:
    if os.path.exists(OUT + name + ".png"):
        print("ATLA (var):", name); continue
    clear(); fn()
print("SET TAMAM")
