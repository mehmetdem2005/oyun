# build_stonewall — sayfanın WALL_SEGMENT'ine en yakın Blender karşılığı
# Katmanlar: blockout (gövde+korniş+3 mazgal) → kabartma (subdiv+displace+bevel)
# → materyal (tuğla doku + pointiness kenar aşınması) → sancak → 3 ışık → Cycles
import bpy, math
from math import radians, sin

S = bpy.context.scene
for o in list(bpy.data.objects): bpy.data.objects.remove(o, do_unlink=True)

def cube(name, sx, sy, sz, x, y, z):
    bpy.ops.mesh.primitive_cube_add(size=1, location=(x, y, z))
    o = bpy.context.object; o.name = name; o.scale = (sx, sy, sz)
    bpy.ops.object.transform_apply(scale=True)
    return o

# ── L1 BLOCKOUT: gövde + korniş + 3 mazgal ──
parts = [cube("body", 2.2, 0.7, 1.5, 0, 0, 0.75),
         cube("ledge", 2.34, 0.8, 0.10, 0, 0, 1.55),
         cube("m1", 0.36, 0.74, 0.24, -0.78, 0, 1.72),
         cube("m2", 0.36, 0.74, 0.24, 0.0, 0, 1.72),
         cube("m3", 0.36, 0.74, 0.24, 0.78, 0, 1.72)]
for o in parts: o.select_set(True)
bpy.context.view_layer.objects.active = parts[0]
bpy.ops.object.join()
wall = bpy.context.object; wall.name = "Wall"

# ── L2 KABARTMA: subdiv + clouds displace (taş düzensizliği) + bevel ──
bev = wall.modifiers.new("bev", 'BEVEL'); bev.width = 0.018; bev.segments = 2
sub = wall.modifiers.new("sub", 'SUBSURF'); sub.subdivision_type = 'SIMPLE'
sub.levels = 4; sub.render_levels = 4
tex = bpy.data.textures.new("rough", 'CLOUDS'); tex.noise_scale = 0.55
disp = wall.modifiers.new("disp", 'DISPLACE'); disp.texture = tex; disp.strength = 0.028

# ── L5 MATERYAL: tuğla rengi + harç + pointiness kenar ışığı + bump ──
m = bpy.data.materials.new("Stone"); m.use_nodes = True
nt = m.node_tree; n = nt.nodes; ln = nt.links
bsdf = n["Principled BSDF"]; bsdf.inputs["Roughness"].default_value = 0.85
tc = n.new("ShaderNodeTexCoord"); mp = n.new("ShaderNodeMapping")
mp.inputs["Scale"].default_value = (1.6, 1.6, 1.6)
mp.inputs["Rotation"].default_value = (radians(90), 0, 0)   # tuğla XZ yüze otursun
br = n.new("ShaderNodeTexBrick")
br.inputs["Color1"].default_value = (0.165, 0.180, 0.214, 1)   # taş açık
br.inputs["Color2"].default_value = (0.090, 0.100, 0.128, 1)   # taş koyu
br.inputs["Mortar"].default_value = (0.030, 0.034, 0.046, 1)   # harç
br.inputs["Scale"].default_value = 1.7
br.inputs["Mortar Size"].default_value = 0.026
br.inputs["Color"] if False else None
ns = n.new("ShaderNodeTexNoise"); ns.inputs["Scale"].default_value = 7.0
mixn = n.new("ShaderNodeMixRGB"); mixn.blend_type = 'MULTIPLY'
mixn.inputs["Fac"].default_value = 0.22
geo = n.new("ShaderNodeNewGeometry")
ramp = n.new("ShaderNodeValToRGB")
ramp.color_ramp.elements[0].position = 0.50
ramp.color_ramp.elements[1].position = 0.62
mixe = n.new("ShaderNodeMixRGB"); mixe.blend_type = 'MIX'
mixe.inputs["Color2"].default_value = (0.42, 0.45, 0.51, 1)    # kenar aşınma açığı
bump = n.new("ShaderNodeBump"); bump.inputs["Strength"].default_value = 0.12
ln.new(tc.outputs["Object"], mp.inputs["Vector"])
ln.new(mp.outputs["Vector"], br.inputs["Vector"])
ln.new(mp.outputs["Vector"], ns.inputs["Vector"])
ln.new(br.outputs["Color"], mixn.inputs["Color1"])
ln.new(ns.outputs["Fac"], mixn.inputs["Color2"])
ln.new(mixn.outputs["Color"], mixe.inputs["Color1"])
ln.new(geo.outputs["Pointiness"], ramp.inputs["Fac"])
ln.new(ramp.outputs["Color"], mixe.inputs["Fac"])
ln.new(mixe.outputs["Color"], bsdf.inputs["Base Color"])
ln.new(br.outputs["Fac"], bump.inputs["Height"])
ln.new(bump.outputs["Normal"], bsdf.inputs["Normal"])
wall.data.materials.append(m)

# ── SANCAK: dalgalı mavi bez + altın elmas + ahşap çubuk ──
bpy.ops.mesh.primitive_grid_add(x_subdivisions=14, y_subdivisions=3, size=1)
ban = bpy.context.object; ban.name = "Banner"; ban.scale = (0.20, 0.36, 1)
bpy.ops.object.transform_apply(scale=True)
for v in ban.data.vertices:
    v.co.z = sin(v.co.x * 22 + v.co.y * 4) * 0.018                 # bez dalgası
ban.rotation_euler = (radians(90), 0, 0)
ban.location = (0, -0.398, 0.94)
mb = bpy.data.materials.new("Blue"); mb.use_nodes = True
pb = mb.node_tree.nodes["Principled BSDF"]
pb.inputs["Base Color"].default_value = (0.044, 0.110, 0.342, 1)  # #2c52a0
pb.inputs["Roughness"].default_value = 0.62
ban.data.materials.append(mb)
bpy.ops.mesh.primitive_cube_add(size=1, location=(0, -0.428, 0.94))
dia = bpy.context.object; dia.name = "Gold"
dia.scale = (0.085, 0.012, 0.085); dia.rotation_euler = (0, radians(45), 0)
mg = bpy.data.materials.new("Gold"); mg.use_nodes = True
pg = mg.node_tree.nodes["Principled BSDF"]
pg.inputs["Base Color"].default_value = (0.66, 0.41, 0.085, 1)    # #d6aa4c
pg.inputs["Metallic"].default_value = 0.9
pg.inputs["Roughness"].default_value = 0.34
dia.data.materials.append(mg)
bpy.ops.mesh.primitive_cylinder_add(radius=0.018, depth=0.52,
    location=(0, -0.392, 1.31), rotation=(0, radians(90), 0))
rod = bpy.context.object
mr = bpy.data.materials.new("Wood"); mr.use_nodes = True
mr.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.13, 0.066, 0.028, 1)
rod.data.materials.append(mr)

# ── IŞIK: sıcak key sol-üst + soğuk dolgu sağ + tepe rim ──
def sun(name, rot, col, e):
    bpy.ops.object.light_add(type='SUN', rotation=rot)
    L = bpy.context.object; L.name = name
    L.data.color = col; L.data.energy = e; L.data.angle = 0.3
    return L
sun("key", (radians(52), 0, radians(-38)), (1.0, 0.90, 0.74), 5.6)
sun("fill", (radians(65), 0, radians(48)), (0.55, 0.66, 1.0), 0.9)
sun("rim", (radians(-35), 0, radians(180)), (0.9, 0.95, 1.0), 2.7)
S.world.use_nodes = True
S.world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.05, 0.06, 0.09, 1)
S.world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.12

# ── KAMERA: ortografik, hafif tepeden ön (sayfa açısı) ──
bpy.ops.object.camera_add(location=(0, -7.5, 2.6),
    rotation=(radians(78), 0, 0))
cam = bpy.context.object; cam.data.type = 'ORTHO'
cam.data.ortho_scale = 2.65; cam.data.shift_y = -0.045
S.camera = cam

# ── RENDER: Cycles CPU, hızlı-temiz ──
S.render.engine = 'CYCLES'
S.cycles.device = 'CPU'
S.cycles.samples = 36
S.cycles.use_adaptive_sampling = True
S.cycles.max_bounces = 4; S.cycles.diffuse_bounces = 2; S.cycles.glossy_bounces = 2
try: S.cycles.use_denoising = True; S.cycles.denoiser = 'OPENIMAGEDENOISE'
except Exception: S.cycles.use_denoising = False
S.render.film_transparent = True
S.render.resolution_x = 540; S.render.resolution_y = 540
S.render.image_settings.color_mode = 'RGBA'
S.render.filepath = "/home/claude/render/wall_raw.png"
bpy.ops.render.render(write_still=True)
print("RENDER TAMAM")
