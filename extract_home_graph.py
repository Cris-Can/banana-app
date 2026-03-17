import os
import re

navigation_path = 'app/src/main/java/com/eventos/banana/navigation/AppNavigation.kt'
navgraphs_path = 'app/src/main/java/com/eventos/banana/navigation/NavGraphs.kt'

with open(navigation_path, 'r', encoding='utf-8') as f:
    nav_text = f.read()

def extract_block(text, start_str):
    start_idx = text.find(start_str)
    if start_idx == -1: return None, text
    
    # find exactly the start of the block `{` after start_str
    brace_start = text.find('{', start_idx)
    nested = 1
    idx = brace_start + 1
    in_str = False
    
    while idx < len(text):
        c = text[idx]
        if c == '"' and text[idx-1] != '\\\\':
            in_str = not in_str
        if not in_str:
            if c == '{': nested += 1
            elif c == '}':
                nested -= 1
                if nested == 0:
                    break
        idx += 1
        
    block = text[start_idx:idx+1]
    new_text = text[:start_idx] + text[idx+1:]
    return block, new_text

home_block, nav_text = extract_block(nav_text, 'composable("home") {')
world_map_block, nav_text = extract_block(nav_text, 'composable("world_map") {')
notifications_block, nav_text = extract_block(nav_text, 'composable("notifications") {')
search_block, nav_text = extract_block(nav_text, 'composable("search") {')

if home_block and world_map_block and notifications_block and search_block:
    home_block = home_block.replace('this@composable', 'this')
    home_block = home_block.replace('this@SharedTransitionLayout', 'sharedTransitionScope')
    
    world_map_block = world_map_block.replace('this@composable', 'this')
    world_map_block = world_map_block.replace('this@SharedTransitionLayout', 'sharedTransitionScope')

    notifications_block = notifications_block.replace('this@composable', 'this')
    notifications_block = notifications_block.replace('this@SharedTransitionLayout', 'sharedTransitionScope')

    search_block = search_block.replace('this@composable', 'this')
    search_block = search_block.replace('this@SharedTransitionLayout', 'sharedTransitionScope')
    
    home_graph = '''
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.homeGraph(
    navController: NavController,
    sessionViewModel: com.eventos.banana.ui.auth.SessionViewModel,
    guideViewModel: com.eventos.banana.ui.onboarding.GuideViewModel,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope
) {
''' + home_block + '\n\n' + world_map_block + '\n\n' + notifications_block + '\n\n' + search_block + '\n}\n'

    with open(navgraphs_path, 'a', encoding='utf-8') as f:
        f.write('\n' + home_graph)
    
    # Insert call to homeGraph in AppNavigation.kt
    replace_target = '''                monetizationGraph(
                    navController = navController,
                    sessionViewModel = sessionViewModel
                )'''
    
    call = replace_target + '''

                // ---------- HOME GRAPH ----------
                homeGraph(
                    navController = navController,
                    sessionViewModel = sessionViewModel,
                    guideViewModel = guideViewModel,
                    sharedTransitionScope = this@SharedTransitionLayout
                )'''
    
    new_nav_text = nav_text.replace(replace_target, call)
    
    if new_nav_text == nav_text:
        print("Failed to replace call in AppNavigation.kt")
    else:
        with open(navigation_path, 'w', encoding='utf-8') as f:
            f.write(new_nav_text)
        print('homeGraph extracted successfully!')
else:
    print('Failed to find one of the blocks.')
