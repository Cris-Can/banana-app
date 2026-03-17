import os

input_file = 'app/src/main/java/com/eventos/banana/navigation/AppNavigation.kt.bak'
output_file = 'app/src/main/java/com/eventos/banana/navigation/NavGraphs.kt'

with open(input_file, 'r', encoding='utf-8') as f:
    lines = f.readlines()

blocks = []
current_start = -1
brace_count = 0

for i, line in enumerate(lines):
    s = line.strip()
    if current_start == -1:
        if s.startswith('composable(') or s.startswith('composable ('):
            rt_match = line.split('"')
            rt = rt_match[1] if len(rt_match) > 1 else line.split('(')[1].split(')')[0]
            current_start = i
            brace_count = line.count('{') - line.count('}')
    else:
        brace_count += line.count('{') - line.count('}')
        if brace_count == 0:
            content = "".join(lines[current_start:i+1])
            blocks.append((current_start, i, content))
            current_start = -1

def get_group(content):
    if '"splash"' in content or '"onboarding"' in content or '"login"' in content or '"verification"' in content: return 'authGraph'
    if '"gold"' in content or '"app_icons"' in content: return 'monetizationGraph'
    if '"home"' in content or '"world_map"' in content or '"notifications"' in content or '"search"' in content: return 'homeGraph'
    if '"create_event"' in content or '"pick_location' in content or '"event_detail' in content or '"questionnaire' in content or '"rate_participants' in content or '"rate_user' in content: return 'eventGraph'
    if '"leaderboard"' in content or '"profile"' in content or '"user_ratings' in content or '"profile_views' in content or '"settings"' in content or '"blocked_users"' in content or '"admin_dashboard"' in content or '"friends' in content or '"public_profile' in content: return 'profileGraph'
    if '"conversations"' in content or '"chat' in content or '"start_chat' in content: return 'chatGraph'
    return 'miscGraph'

groups = { 'authGraph': [], 'monetizationGraph': [], 'homeGraph': [], 'eventGraph': [], 'profileGraph': [], 'chatGraph': [], 'miscGraph': [] }

for start, end, content in blocks:
    g = get_group(content)
    groups[g].append(content)

print(f"Misc blocks length: {len(groups['miscGraph'])}")

pkg_and_imports = ""
for line in lines:
    if line.startswith('@Composable') or 'fun AppNavigation' in line:
        break
    pkg_and_imports += line

imports_only = "".join(l for l in pkg_and_imports.splitlines(True) if l.startswith('import '))
header = "package com.eventos.banana.navigation\n\n" + imports_only + """
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import com.eventos.banana.ui.session.SessionViewModel
import android.content.SharedPreferences
import com.eventos.banana.ui.onboarding.GuideViewModel
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.MutableState
"""

generated_functions = ""

for funcName, blocks_list in groups.items():
    if not blocks_list: continue

    if funcName in ['homeGraph', 'eventGraph', 'miscGraph']:
        decl = f"@OptIn(ExperimentalSharedTransitionApi::class)\nfun NavGraphBuilder.{funcName}("
        decl += "navController: NavController, sessionViewModel: SessionViewModel, guideViewModel: GuideViewModel, sharedTransitionScope: SharedTransitionScope) {\n"
    elif funcName == 'authGraph':
        decl = f"fun NavGraphBuilder.{funcName}(navController: NavController, sessionViewModel: SessionViewModel, sharedPreferences: SharedPreferences, hasSeenOnboarding: MutableState<Boolean>) {{\n"
    else:
        decl = f"fun NavGraphBuilder.{funcName}(navController: NavController, sessionViewModel: SessionViewModel) {{\n"
    
    decl += "    val context = androidx.compose.ui.platform.LocalContext.current\n\n"
    
    for content in blocks_list:
        indented = "\n".join("    " + l for l in content.splitlines())
        # FIX: Inside the generated file, 'this@composable' is no longer valid for AnimatedVisibilityScope because we are not in the same file/structure.
        # But actually, `this` inside `composable { ... }` is precisely the AnimatedVisibilityScope!
        # So we can just replace 'this@composable' with 'this'.
        indented = indented.replace("this@composable", "this")
        decl += indented + "\n\n"
        
    decl += "}\n\n"
    generated_functions += decl

with open(output_file, 'w', encoding='utf-8') as f:
    f.write(header + generated_functions)

min_i = min(b[0] for b in blocks)
max_i = max(b[1] for b in blocks)

new_app_nav = "".join(lines[:min_i])
calls = """
                // ---------- SUB-GRAPHS POR FEATURE ----------
                authGraph(navController, sessionViewModel, sharedPreferences, hasSeenOnboarding)
                homeGraph(navController, sessionViewModel, guideViewModel, this@SharedTransitionLayout)
                eventGraph(navController, sessionViewModel, guideViewModel, this@SharedTransitionLayout)
                profileGraph(navController, sessionViewModel)
                chatGraph(navController, sessionViewModel)
                monetizationGraph(navController, sessionViewModel)
"""
if groups['miscGraph']:
    calls += "                miscGraph(navController, sessionViewModel, guideViewModel, this@SharedTransitionLayout)\n"

new_app_nav += calls
new_app_nav += "".join(lines[max_i+1:])

with open('app/src/main/java/com/eventos/banana/navigation/AppNavigation.kt', 'w', encoding='utf-8') as f:
    f.write(new_app_nav)
