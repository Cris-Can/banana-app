import sys

with open('app/src/main/java/com/eventos/banana/navigation/AppNavigation.kt.bak', 'r', encoding='utf-8') as f:
    text = f.read()

# Identify start of NavHost
navhost_start = text.find('NavHost(')
if navhost_start == -1: sys.exit(1)

# we need to find the ` {` of the `NavHost` call
nested = 0
in_navhost_args = True
navhost_body_start = -1

for i in range(navhost_start, len(text)):
    c = text[i]
    if c == '(':
        nested += 1
    elif c == ')':
        nested -= 1
    elif c == '{' and nested == 0:
        navhost_body_start = i
        break

if navhost_body_start == -1: sys.exit(1)

# Find end of NavHost body
nested = 1
navhost_body_end = -1
for i in range(navhost_body_start + 1, len(text)):
    c = text[i]
    if c == '{': nested += 1
    elif c == '}':
        nested -= 1
        if nested == 0:
            navhost_body_end = i
            break

navhost_body = text[navhost_body_start + 1 : navhost_body_end]

# Extract all top-level composable calls from navhost_body
composables = [] # List of tuples: (substring, route)
i = 0
while i < len(navhost_body):
    substr = navhost_body[i:]
    # look for 'composable(' or 'composable (' at the top level
    idx = substr.find('composable')
    if idx == -1: break
    
    # ensure it's not a word part
    if idx > 0 and substr[idx-1].isalnum(): 
        i += idx + 10
        continue
    
    # Verify the next non-whitespace char is '('
    j = idx + 10
    while j < len(substr) and substr[j].isspace(): j += 1
    if j < len(substr) and substr[j] != '(': 
        i += idx + 10
        continue
    
    # We found a compensable( call. Parse it until the end of its `{ ... }`
    comp_start = idx
    # first find `)`
    paren_nested = 0
    in_str = False
    k = j
    paren_end = -1
    route_name = "unknown"
    while k < len(substr):
        c = substr[k]
        if c == '"' and substr[k-1] != '\\': in_str = not in_str
        if not in_str:
            if c == '(': paren_nested += 1
            elif c == ')':
                paren_nested -= 1
                if paren_nested == 0:
                    paren_end = k
                    break
        k += 1
    
    # Extract route name for classifying
    args_str = substr[j+1 : paren_end]
    # Simple regex to get the first string literal argument, typically the route
    if '"' in args_str:
        route_name = args_str.split('"')[1]
    
    # Now find the trailing block `{ ... }`
    k = paren_end + 1
    while k < len(substr) and substr[k].isspace(): k += 1
    if k < len(substr) and substr[k] == '{':
        brace_start = k
        brace_nested = 1
        k += 1
        in_str = False
        while k < len(substr):
            c = substr[k]
            if c == '"' and substr[k-1] != '\\': in_str = not in_str
            if not in_str:
                if c == '{': brace_nested += 1
                elif c == '}':
                    brace_nested -= 1
                    if brace_nested == 0:
                        break
            k += 1
        
        comp_end = k + 1
        comp_content = substr[comp_start:comp_end]
        composables.append((comp_content, route_name))
        i += comp_end
    else:
        i += paren_end + 1


def get_group(rt):
    for k in ['splash', 'onboarding', 'login', 'verification']: 
        if k in rt: return 'authGraph'
    for k in ['gold', 'app_icons']: 
        if k in rt: return 'monetizationGraph'
    for k in ['home', 'world_map', 'notifications', 'search']: 
        if k in rt: return 'homeGraph'
    for k in ['create_event', 'pick_location', 'event_detail', 'questionnaire', 'rate_participants', 'rate_user']: 
        if k in rt: return 'eventGraph'
    for k in ['leaderboard', 'profile', 'user_ratings', 'profile_views', 'settings', 'blocked_users', 'admin_dashboard', 'friends', 'public_profile']: 
        if k in rt: return 'profileGraph'
    for k in ['conversations', 'chat', 'start_chat']: 
        if k in rt: return 'chatGraph'
    return 'miscGraph'

groups = { 'authGraph': [], 'monetizationGraph': [], 'homeGraph': [], 'eventGraph': [], 'profileGraph': [], 'chatGraph': [], 'miscGraph': [] }

for c, r in composables:
    groups[get_group(r)].append(c)

# Build NavGraphs.kt
pkg_and_imports = ""
for line in text.split('\n'):
    if line.startswith('@Composable') or 'fun AppNavigation' in line: break
    pkg_and_imports += line + "\n"

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
        indented = indented.replace("this@composable", "this")
        decl += indented + "\n\n"
        
    decl += "}\n\n"
    generated_functions += decl

with open('app/src/main/java/com/eventos/banana/navigation/NavGraphs.kt', 'w', encoding='utf-8') as f:
    f.write(header + generated_functions)

# Rebuild AppNavigation.kt
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

new_navhost_body = calls
# Some extra non-composable code might be inside navhost_body, we just discard everything except the overlay. Wait!
# Actually, the guide overlay is OUTSIDE NavHost. It's in the Box. 
# So we can just replace the whole navhost_body with our calls
# But we need to keep `startDestination = "splash"`! Wait, we are replacing the `body` of NavHost.
# So `navHost` body is entirely composed of composable() calls.

new_app_nav = text[:navhost_body_start + 1] + new_navhost_body + text[navhost_body_end:]

with open('app/src/main/java/com/eventos/banana/navigation/AppNavigation.kt', 'w', encoding='utf-8') as f:
    f.write(new_app_nav)

print(f"Refactor using precise parser done. Found {len(composables)} composables.")
