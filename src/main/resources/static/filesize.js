(function($){

        function removeConfig (element) {
            var $container = $('.filesize-hooks');
            $(element).parents('.filesize-config-group').remove();

            var $visibleElements = $container.find('.filesize-config-group');
            if ($visibleElements.length == 1) {
                $visibleElements.find(".filesize-hook-delete").addClass('filesize-hook-hidden');
            }

            $visibleElements.each(function(index, visibleElement) {
                var $currentElement = $(visibleElement);
                var $configElementIndex = index + 1;
                $currentElement.attr('id', 'filesize-hook-' + $configElementIndex);
                $currentElement.find('.filesize-hook-include-pattern').find('label').attr('for', 'pattern-' + $configElementIndex);
                $currentElement.find('.filesize-hook-include-pattern').find('input').attr('id', 'pattern-' + $configElementIndex)
                    .attr('name', 'pattern-' + $configElementIndex);
                $currentElement.find('.filesize-hook-size').find('label').attr('for', 'size-' + $configElementIndex);
                $currentElement.find('.filesize-hook-size').find('input').attr('id', 'size-' + $configElementIndex)
                    .attr('name', 'size-' + $configElementIndex);
                $currentElement.find('.filesize-hook-exclude-pattern').find('label').attr('for', 'pattern-exclude-' + $configElementIndex);
                $currentElement.find('.filesize-hook-exclude-pattern').find('input').attr('id', 'pattern-exclude-' + $configElementIndex)
                    .attr('name', 'pattern-exclude-' + $configElementIndex);
                $currentElement.find('.filesize-hook-branches-pattern').find('label').attr('for', 'pattern-branches-' + $configElementIndex);
                $currentElement.find('.filesize-hook-branches-pattern').find('input').attr('id', 'pattern-branches-' + $configElementIndex)
                    .attr('name', 'pattern-branches-' + $configElementIndex);
                $currentElement.find('.filesize-hook-delete').attr('id', 'remove-button-' + $configElementIndex);
            });

            var $controlAdd = $container.find('#filesize-hook-add');
            $controlAdd.removeClass('filesize-hook-hidden');
        };

        function addConfig (element) {
            var $maxInputs = parseInt($(element).attr('data-max-inputs'));
            var $container = $('.filesize-hooks');
            var $controlAdd = $container.find('#filesize-hook-add');
            var $existingInputs = $container.children('.filesize-config-group');

            var html = stash.filehooks.size.anotherConfig({
                'canDelete' : true,
                'count' : $existingInputs.length+1
            });
            $(html).insertBefore($controlAdd.parent());

            $controlAdd.toggleClass('filesize-hook-hidden', $existingInputs.length + 1 === $maxInputs);
            $container.find('.filesize-hook-delete').removeClass('filesize-hook-hidden');
        };

        $(document).on('click', '.filesize-hook-add', function(e) {
            e.preventDefault();
            addConfig(this);
        });

        $(document).on('click', '.filesize-hook-delete', function (e) {
            e.preventDefault();
            removeConfig(this);
        });

}(AJS.$));